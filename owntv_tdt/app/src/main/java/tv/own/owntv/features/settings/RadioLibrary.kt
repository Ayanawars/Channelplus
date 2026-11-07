package tv.own.owntv.features.settings

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** One radio station. No IPTV television source or category is reused. */
data class StayonRadioStation(
    val id: String,
    val name: String,
    val url: String,
    val logo: String?,
    val playlistId: String? = null,
)

data class StayonRadioPlaylist(
    val id: String,
    val name: String,
    val url: String,
    val syncedAt: Long,
)

/**
 * Independent Radio IPTV sources and single streams. Persisted globally, not in legacy
 * user profiles and never in the television playlist tables.
 *
 * Radio lists must be explicitly added by the user; TV M3U imports aren't harvested for radio.
 * Failed refresh preserves the old stations and leaves other lists untouched.
 */
class StayonRadioLibrary(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("stayon_radio_library_v1", Context.MODE_PRIVATE)
    private val _stations = MutableStateFlow(parseStations(prefs.getString("stations", "[]")))
    private val _playlists = MutableStateFlow(parsePlaylists(prefs.getString("playlists", "[]")))
    val stations: StateFlow<List<StayonRadioStation>> = _stations
    val playlists: StateFlow<List<StayonRadioPlaylist>> = _playlists

    @Synchronized
    fun addStation(name: String, url: String, logo: String?): StayonRadioStation {
        require(name.isNotBlank())
        require(isStreamUrl(url))
        val normalizedLogo = logo?.trim()?.takeIf(::isStreamUrl)
        val station = StayonRadioStation(UUID.randomUUID().toString(), name.trim(), url.trim(), normalizedLogo)
        saveStations(_stations.value + station)
        return station
    }

    @Synchronized
    fun deleteStation(id: String) {
        saveStations(_stations.value.filterNot { it.id == id })
    }

    /** Add and sync an independent radio M3U source; publish only after it has usable stations. */
    suspend fun addPlaylist(name: String, url: String): Int {
        require(name.isNotBlank())
        require(isStreamUrl(url))
        val parsed = fetchPlaylist(url)
        require(parsed.isNotEmpty())
        synchronized(this) {
            // Avoid silently adding a second copy of the same radio playlist.
            require(_playlists.value.none { it.url.trim().equals(url.trim(), true) })
            val id = UUID.randomUUID().toString()
            val playlist = StayonRadioPlaylist(id, name.trim(), url.trim(), System.currentTimeMillis())
            savePlaylists(_playlists.value + playlist)
            saveStations(_stations.value + parsed.mapIndexed { index, station ->
                station.copy(id = id + ":" + index, playlistId = id)
            })
        }
        return parsed.size
    }

    suspend fun refreshPlaylist(id: String): Int {
        val source = _playlists.value.firstOrNull { it.id == id } ?: throw NoSuchElementException()
        val parsed = fetchPlaylist(source.url)
        require(parsed.isNotEmpty())
        synchronized(this) {
            val surviving = _stations.value.filterNot { it.playlistId == id }
            val updated = parsed.mapIndexed { index, station ->
                station.copy(id = id + ":" + index, playlistId = id)
            }
            saveStations(surviving + updated)
            savePlaylists(_playlists.value.map {
                if (it.id == id) it.copy(syncedAt = System.currentTimeMillis()) else it
            })
        }
        return parsed.size
    }

    @Synchronized
    fun deletePlaylist(id: String) {
        savePlaylists(_playlists.value.filterNot { it.id == id })
        saveStations(_stations.value.filterNot { it.playlistId == id })
    }

    private fun saveStations(rows: List<StayonRadioStation>) {
        prefs.edit().putString("stations", JSONArray().apply {
            rows.forEach { row -> put(JSONObject().apply {
                put("id", row.id); put("name", row.name); put("url", row.url)
                put("logo", row.logo); put("playlistId", row.playlistId)
            }) }
        }.toString()).apply()
        _stations.value = rows
    }

    private fun savePlaylists(rows: List<StayonRadioPlaylist>) {
        prefs.edit().putString("playlists", JSONArray().apply {
            rows.forEach { row -> put(JSONObject().apply {
                put("id", row.id); put("name", row.name); put("url", row.url)
                put("syncedAt", row.syncedAt)
            }) }
        }.toString()).apply()
        _playlists.value = rows
    }

    private suspend fun fetchPlaylist(sourceUrl: String): List<StayonRadioStation> = withContext(Dispatchers.IO) {
        require(isStreamUrl(sourceUrl))
        val connection = URL(sourceUrl.trim()).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "audio/x-mpegurl, application/vnd.apple.mpegurl, text/plain, */*")
            connection.instanceFollowRedirects = true
            require(connection.responseCode in 200..299)
            // Bound download size to avoid untrusted playlists exhausting TV memory.
            val content = connection.inputStream.use { input ->
                val bytes = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8_192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    require(bytes.size() + n <= 8 * 1024 * 1024)
                    bytes.write(buf, 0, n)
                }
                bytes.toString(Charsets.UTF_8.name())
            }
            parseM3u(content)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        fun isStreamUrl(url: String): Boolean {
            val trim = url.trim()
            return trim.startsWith("https://", true) || trim.startsWith("http://", true)
        }

        /** Radio-specific M3U parser, independent from the TV library and its sync. */
        fun parseM3u(text: String): List<StayonRadioStation> {
            val items = ArrayList<StayonRadioStation>()
            var pendingName: String? = null
            var pendingLogo: String? = null
            val logoPattern = Regex("""tvg-logo\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            for (raw in text.lineSequence()) {
                val line = raw.trim().removePrefix("\uFEFF")
                if (line.startsWith("#EXTINF", true)) {
                    pendingName = line.substringAfterLast(',', "").trim().ifBlank { null }
                    pendingLogo = logoPattern.find(line)?.groupValues?.getOrNull(1)
                    continue
                }
                if (line.isBlank() || line.startsWith("#")) continue
                if (isStreamUrl(line)) {
                    if (items.size >= 5_000) break
                    val name = pendingName ?: runCatching {
                        URL(line).host.ifBlank { line.substringBefore('?') }
                    }.getOrDefault(line)
                    items += StayonRadioStation(
                        id = line.hashCode().toUInt().toString(),
                        name = name,
                        url = line,
                        logo = pendingLogo?.takeIf(::isStreamUrl),
                    )
                }
                pendingName = null
                pendingLogo = null
            }
            return items.distinctBy { it.url }
        }

        private fun parseStations(raw: String?): List<StayonRadioStation> = runCatching {
            val array = JSONArray(raw ?: "[]")
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                StayonRadioStation(
                    item.getString("id"), item.getString("name"), item.getString("url"),
                    item.optString("logo").takeUnless { it.isBlank() || it == "null" },
                    item.optString("playlistId").takeUnless { it.isBlank() || it == "null" },
                )
            }
        }.getOrDefault(emptyList())

        private fun parsePlaylists(raw: String?): List<StayonRadioPlaylist> = runCatching {
            val array = JSONArray(raw ?: "[]")
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                StayonRadioPlaylist(
                    item.getString("id"), item.getString("name"), item.getString("url"),
                    item.optLong("syncedAt", 0),
                )
            }
        }.getOrDefault(emptyList())
    }
}
