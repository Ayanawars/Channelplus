package tv.own.owntv.core.sync

import android.content.Context
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.model.TifChannelUri

/** Local source stored in the existing source schema. Its scheme dispatches before any M3U parsing. */
class TunerSourceSyncer(
    private val context: Context,
    private val categories: CategoryDao,
    private val channels: ChannelDao,
    private val epg: EpgDao?,
) {
    data class Input(
        val id: String,
        val name: String,
        val providerPackage: String? = null,
        val filterByOwner: Boolean = false,
    )
    private data class Station(val id: Long, val name: String, val number: Int?)

    suspend fun sync(source: SourceEntity) {
        val sourceUri = Uri.parse(source.url)
        val input = sourceUri.getQueryParameter("input")
            ?: error(context.getString(tv.own.owntv.core.R.string.tuner_invalid_source))
        val provider = sourceProvider(sourceUri)
        val ownerFilter = if (sourceUri.getQueryParameter("ownerFilter") == "1") provider else null
        val manager = context.getSystemService(TvInputManager::class.java)
        check(manager?.tvInputList?.any { it.id == input } == true) { context.getString(tv.own.owntv.core.R.string.tuner_missing) }
        val stations = readStations(input, ownerFilter)
        // A denied/filtered read must never erase the previous working list.
        check(stations.isNotEmpty()) { context.getString(tv.own.owntv.core.R.string.tuner_empty) }
        val categoryKey = if (provider == PLUTO_PACKAGE) "pluto:$input" else "antenna"
        val categoryName = if (provider == PLUTO_PACKAGE) PLUTO_LABEL else context.getString(tv.own.owntv.core.R.string.tuner_antenna_name)
        var category = categories.findByRemoteIds(source.id, MediaType.LIVE, listOf(categoryKey)).firstOrNull()
        if (category == null) {
            categories.insertAll(listOf(CategoryEntity(sourceId = source.id, mediaType = MediaType.LIVE, name = categoryName, remoteId = categoryKey)))
            category = categories.findByRemoteIds(source.id, MediaType.LIVE, listOf(categoryKey)).first()
        }
        val categoryId = requireNotNull(category).id
        val old = stations.map { "tif:${it.id}" }.chunked(200)
            .flatMap { channels.findByRemoteIds(source.id, it) }.associateBy { it.remoteId }
        val rows = stations.sortedWith(compareBy<Station> { it.number ?: Int.MAX_VALUE }.thenBy { it.name }).mapIndexed { order, ch ->
            val key = "tif:${ch.id}"
            ChannelEntity(id = old[key]?.id ?: 0, sourceId = source.id, categoryId = categoryId,
                name = ch.name, number = ch.number, remoteId = key, sortOrder = order,
                logoUrl = TvContract.buildChannelLogoUri(ch.id).toString(),
                epgChannelId = "tif:$input:${ch.id}", streamUrl = TifChannelUri(input, ch.id).encode())
        }
        currentCoroutineContext().ensureActive()
        rows.filter { it.id != 0L }.chunked(200).forEach { channels.updateAll(it) }
        rows.filter { it.id == 0L }.chunked(200).forEach { channels.insertAll(it) }
        val retained = rows.mapNotNull { it.remoteId }.toSet()
        channels.remoteIdsForSource(source.id).filterNot { it in retained }.chunked(200).forEach { channels.deleteByRemoteIds(source.id, it) }
        if (epg != null) {
            val now = System.currentTimeMillis()
            for (station in stations) {
                currentCoroutineContext().ensureActive()
                // EPG failures do not prevent watching TV input channels or discard a previous guide.
                try { importGuide(source.id, input, station, now) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { android.util.Log.w("TunerSource", "EPG unavailable for channel ${station.id}: ${e.javaClass.simpleName}") }
            }
        }
    }

    private suspend fun readStations(input: String, ownerFilter: String? = null): List<Station> {
        val columns = arrayOf(TvContract.Channels._ID, TvContract.Channels.COLUMN_DISPLAY_NAME,
            TvContract.Channels.COLUMN_DISPLAY_NUMBER, TvContract.Channels.COLUMN_SERVICE_TYPE,
            TvContract.Channels.COLUMN_PACKAGE_NAME)
        val stations = ArrayList<Station>()
        context.contentResolver.query(TvContract.buildChannelsUriForInput(input), columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                currentCoroutineContext().ensureActive()
                if (c.getString(3) == TvContract.Channels.SERVICE_TYPE_AUDIO) continue
                if (ownerFilter != null && c.getString(4) != ownerFilter) continue
                stations += Station(c.getLong(0), c.getString(1) ?: context.getString(tv.own.owntv.core.R.string.tuner_channel_name, c.getLong(0)), c.getString(2)?.toIntOrNull())
            }
        }
        return stations
    }

    suspend fun refreshGuide(source: SourceEntity, storeId: Long = source.id): Int {
        val sourceUri = Uri.parse(source.url)
        val input = sourceUri.getQueryParameter("input") ?: error(context.getString(tv.own.owntv.core.R.string.tuner_missing))
        val ownerFilter = if (sourceUri.getQueryParameter("ownerFilter") == "1") sourceProvider(sourceUri) else null
        val stations = readStations(input, ownerFilter)
        check(stations.isNotEmpty()) { context.getString(tv.own.owntv.core.R.string.tuner_empty) }
        val now = System.currentTimeMillis()
        var count = 0
        for (station in stations) {
            currentCoroutineContext().ensureActive()
            count += importGuide(storeId, input, station, now)
        }
        return count
    }

    private suspend fun importGuide(sourceId: Long, input: String, station: Station, now: Long): Int {
        val dao = epg ?: return 0
        val key = "tif:$input:${station.id}"
        val programmes = ArrayList<EpgProgrammeEntity>()
        val columns = arrayOf(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS, TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS,
            TvContract.Programs.COLUMN_TITLE, TvContract.Programs.COLUMN_SHORT_DESCRIPTION)
        val uri = TvContract.buildProgramsUriForChannel(station.id, now - 3600000L, now + 2 * 86400000L)
        val cursor = context.contentResolver.query(uri, columns, null, null, TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS + " ASC") ?: return 0
        cursor.use { c ->
            while (c.moveToNext() && programmes.size < 2000) {
                currentCoroutineContext().ensureActive()
                val start = c.getLong(0); val stop = c.getLong(1)
                if (stop > start) programmes += EpgProgrammeEntity(sourceId = sourceId, epgChannelId = key,
                    startMs = start, stopMs = stop, title = c.getString(2) ?: context.getString(tv.own.owntv.core.R.string.tuner_unknown_programme), description = c.getString(3))
            }
        }
        dao.upsertChannels(listOf(EpgChannelEntity(sourceId = sourceId, epgChannelId = key, displayName = station.name)))
        // Only replace a programme when the provider actually supplies it. Empty reads preserve cache.
        programmes.chunked(200).forEach { dao.upsertProgrammes(it) }
        return programmes.size
    }

    companion object {
        const val READ_PERMISSION = "android.permission.READ_TV_LISTINGS"
        private const val PLUTO_PACKAGE = "tv.pluto.android"
        private const val PLUTO_LABEL = "Pluto TV"
        fun isPluto(providerPackage: String?): Boolean = providerPackage == PLUTO_PACKAGE
        fun isSource(url: String) = url.startsWith("owntv-tuner://")
        fun sourceUrl(id: String, providerPackage: String? = null, filterByOwner: Boolean = false): String = Uri.Builder()
            .scheme("owntv-tuner").authority("input").appendQueryParameter("input", id)
            .apply {
                if (providerPackage != null) appendQueryParameter("provider", providerPackage)
                if (filterByOwner) appendQueryParameter("ownerFilter", "1")
            }.build().toString()

        private fun sourceProvider(uri: Uri): String? = uri.getQueryParameter("provider")?.takeIf { it == PLUTO_PACKAGE }

        fun discover(context: Context): List<Input> {
            val available = context.getSystemService(TvInputManager::class.java)?.tvInputList.orEmpty()
                .filterNot { it.isPassthroughInput }
            val hardware = available.filter { it.type == TvInputInfo.TYPE_TUNER && it.id.contains("/HW") }
                .map { Input(it.id, it.loadLabel(context).toString()) }
            val pluto = linkedMapOf<String, Input>()
            available.filter { info ->
                val label = info.loadLabel(context).toString()
                val isPlutoInput = info.serviceInfo.packageName == PLUTO_PACKAGE ||
                    label.contains(PLUTO_LABEL.substringBefore(' '), ignoreCase = true)
                isPlutoInput && hasChannels(context, info.id)
            }.forEach { info ->
                val label = info.loadLabel(context).toString().ifBlank { PLUTO_LABEL }
                pluto[info.id] = Input(
                    info.id,
                    label,
                    PLUTO_PACKAGE,
                    filterByOwner = hasPlutoOwnedChannels(context, info.id),
                )
            }

            val projection = arrayOf(TvContract.Channels.COLUMN_INPUT_ID, TvContract.Channels.COLUMN_PACKAGE_NAME,
                TvContract.Channels.COLUMN_SERVICE_TYPE)
            runCatching {
                context.contentResolver.query(TvContract.Channels.CONTENT_URI, projection,
                    "${TvContract.Channels.COLUMN_PACKAGE_NAME} = ?", arrayOf(PLUTO_PACKAGE), null)?.use { cursor ->
                    val availableIds = available.mapTo(hashSetOf()) { it.id }
                    while (cursor.moveToNext()) {
                        val inputId = cursor.getString(0) ?: continue
                        if (inputId !in availableIds || cursor.getString(2) == TvContract.Channels.SERVICE_TYPE_AUDIO) continue
                        val existing = pluto[inputId]
                        pluto[inputId] = if (existing != null) {
                            existing.copy(filterByOwner = true)
                        } else {
                            Input(inputId, PLUTO_LABEL, PLUTO_PACKAGE, filterByOwner = true)
                        }
                    }
                }
            }
            return hardware + pluto.values
        }

        private fun hasPlutoOwnedChannels(context: Context, inputId: String): Boolean = runCatching {
            val projection = arrayOf(TvContract.Channels.COLUMN_PACKAGE_NAME, TvContract.Channels.COLUMN_SERVICE_TYPE)
            context.contentResolver.query(TvContract.buildChannelsUriForInput(inputId), projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(0) == PLUTO_PACKAGE && cursor.getString(1) != TvContract.Channels.SERVICE_TYPE_AUDIO) {
                        return@use true
                    }
                }
                false
            } ?: false
        }.getOrDefault(false)

        private fun hasChannels(context: Context, inputId: String): Boolean = runCatching {
            context.contentResolver.query(TvContract.buildChannelsUriForInput(inputId),
                arrayOf(TvContract.Channels._ID), null, null, null)?.use { it.moveToFirst() } ?: false
        }.getOrDefault(false)
    }
}
