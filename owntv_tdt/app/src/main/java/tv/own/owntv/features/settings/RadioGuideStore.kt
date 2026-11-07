package tv.own.owntv.features.settings

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.parser.XmltvParser
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.PushbackInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import java.util.zip.GZIPInputStream

internal data class RadioGuideProgramme(val start: Long, val end: Long, val title: String)
internal data class RadioGuideChannel(
    val id: String,
    val name: String?,
    val logo: String?,
    val programmes: List<RadioGuideProgramme>,
) {
    val matchingName = name?.let(::radioGuideName)
}
internal data class RadioGuideFeed(
    val id: String,
    val name: String,
    val url: String,
    val updated: Long,
    val channels: List<RadioGuideChannel>,
)

internal fun radioGuideMatch(
    feeds: List<RadioGuideFeed>,
    station: StayonRadioStation,
    now: Long,
): Pair<RadioGuideProgramme?, String?> {
    val name = radioGuideName(station.name)
    val id = station.guideId?.trim()
    var programme: RadioGuideProgramme? = null
    var logo: String? = null
    for (feed in feeds) {
        val match = id?.let { key -> feed.channels.singleOrNull { it.id.equals(key, ignoreCase = true) } }
            ?: feed.channels.singleOrNull { name.isNotEmpty() && it.matchingName == name }
            ?: continue
        if (logo == null) logo = match.logo
        if (programme == null) programme = match.programmes.firstOrNull { now >= it.start && now < it.end }
        if (programme != null && logo != null) break
    }
    return programme to logo
}

private val radioGuideMarks = Regex("\\p{M}+")
private val radioGuideSeparators = Regex("[^\\p{L}\\p{N}]+")
private fun radioGuideName(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD).replace(radioGuideMarks, "")
        .lowercase(Locale.ROOT).replace(radioGuideSeparators, "")

/** XMLTV snapshots are independent of the TV guide. A failed fetch keeps the previous feed. */
internal class RadioGuideStore private constructor(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "stayon-radio-epg.json"))
    private val lock = Mutex()
    private val state = MutableStateFlow<List<RadioGuideFeed>>(emptyList())
    val feeds = state.asStateFlow()
    private var loaded = false

    suspend fun load() = withContext(Dispatchers.IO) { lock.withLock { loadLocked() } }

    suspend fun save(id: String?, name: String, url: String) = withContext(Dispatchers.IO) {
        require(name.isNotBlank() && StayonRadioLibrary.isStreamUrl(url))
        lock.withLock {
            loadLocked()
            val old = state.value
            require(if (id == null) old.size < 4 else old.any { it.id == id })
            require(old.none { it.id != id && it.url == url })
            val feed = RadioGuideFeed(id ?: UUID.randomUUID().toString(), name, url, System.currentTimeMillis(), fetch(url))
            persist(if (id == null) old + feed else old.map { if (it.id == id) feed else it })
        }
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        lock.withLock { loadLocked(); persist(state.value.filterNot { it.id == id }) }
    }

    suspend fun move(id: String, delta: Int) = withContext(Dispatchers.IO) {
        lock.withLock {
            loadLocked()
            val rows = state.value.toMutableList()
            val index = rows.indexOfFirst { it.id == id }
            if (index >= 0 && index + delta in rows.indices) {
                rows.add(index + delta, rows.removeAt(index))
                persist(rows)
            }
        }
    }

    private fun loadLocked() {
        if (loaded) return
        state.value = runCatching {
            JSONArray(file.readFully().toString(Charsets.UTF_8)).objects().map { feed ->
                RadioGuideFeed(feed.getString("id"), feed.getString("name"), feed.getString("url"),
                    feed.getLong("updated"), feed.getJSONArray("channels").objects().map { channel ->
                        RadioGuideChannel(channel.getString("id"), channel.optString("name").takeIf(String::isNotBlank),
                            channel.optString("logo").takeIf { StayonRadioLibrary.isStreamUrl(it) },
                            channel.getJSONArray("programmes").objects().map {
                                RadioGuideProgramme(it.getLong("start"), it.getLong("end"), it.getString("title"))
                            })
                    })
            }
        }.getOrDefault(emptyList())
        loaded = true
    }

    private fun persist(rows: List<RadioGuideFeed>) {
        val data = JSONArray(rows.map { feed ->
            JSONObject().put("id", feed.id).put("name", feed.name).put("url", feed.url).put("updated", feed.updated)
                .put("channels", JSONArray(feed.channels.map { channel ->
                    JSONObject().put("id", channel.id).put("name", channel.name).put("logo", channel.logo)
                        .put("programmes", JSONArray(channel.programmes.map {
                            JSONObject().put("start", it.start).put("end", it.end).put("title", it.title)
                        }))
                }))
        }).toString().toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try {
            stream.write(data)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
        state.value = rows
    }

    private suspend fun fetch(url: String): List<RadioGuideChannel> {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            require(connection.responseCode in 200..299)
            val channels = linkedMapOf<String, RadioGuideChannel>()
            val programmes = mutableMapOf<String, MutableList<RadioGuideProgramme>>()
            val now = System.currentTimeMillis()
            var count = 0
            connection.inputStream.use { raw ->
                val wire = RadioGuideLimit(raw, 16 * 1024 * 1024)
                val compressed = PushbackInputStream(wire, 2)
                val first = compressed.read()
                val second = compressed.read()
                if (second >= 0) compressed.unread(second)
                if (first >= 0) compressed.unread(first)
                val input = if (first == 0x1f && second == 0x8b) GZIPInputStream(compressed) else compressed
                RadioGuideLimit(input, 32 * 1024 * 1024).use { bounded ->
                    XmltvParser.parse(bounded, onChannel = { id, name, logo ->
                        if (channels.size < 3_000) channels[id] = RadioGuideChannel(id, name, logo, emptyList())
                    }, onProgramme = { id, start, end, title, _, _ ->
                        if (end > now && start < now + 48 * 3_600_000L && count < 12_000) {
                            programmes.getOrPut(id) { mutableListOf() }.add(RadioGuideProgramme(start, end, title.take(512)))
                            count++
                        }
                    })
                    check(!bounded.exceeded && !wire.exceeded)
                }
            }
            require(channels.isNotEmpty())
            return channels.values.map { it.copy(programmes = programmes[it.id].orEmpty().sortedBy { p -> p.start }) }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        @Volatile private var instance: RadioGuideStore? = null
        fun shared(context: Context): RadioGuideStore = instance ?: synchronized(this) {
            instance ?: RadioGuideStore(context.applicationContext).also { instance = it }
        }
    }
}

private fun JSONArray.objects(): List<JSONObject> = List(length(), ::getJSONObject)

private class RadioGuideLimit(input: InputStream, private var remaining: Int) : FilterInputStream(input) {
    var exceeded = false
        private set

    override fun read(): Int {
        val result = super.read()
        if (result >= 0) consume(1)
        return result
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        val result = super.read(bytes, offset, length)
        if (result > 0) consume(result)
        return result
    }

    private fun consume(bytes: Int) {
        remaining -= bytes
        if (remaining < 0) {
            exceeded = true
            throw java.io.IOException()
        }
    }
}
