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
    data class Input(val id: String, val name: String)
    private data class Station(val id: Long, val name: String, val number: Int?)

    suspend fun sync(source: SourceEntity) {
        val input = Uri.parse(source.url).getQueryParameter("input")
            ?: error(context.getString(tv.own.owntv.core.R.string.tuner_invalid_source))
        val manager = context.getSystemService(TvInputManager::class.java)
        check(manager?.tvInputList?.any { it.id == input } == true) { context.getString(tv.own.owntv.core.R.string.tuner_missing) }
        val stations = readStations(input)
        // A denied/filtered read must never erase the previous working list.
        check(stations.isNotEmpty()) { context.getString(tv.own.owntv.core.R.string.tuner_empty) }
        val categoryKey = "antenna"
        var category = categories.findByRemoteIds(source.id, MediaType.LIVE, listOf(categoryKey)).firstOrNull()
        if (category == null) {
            categories.insertAll(listOf(CategoryEntity(sourceId = source.id, mediaType = MediaType.LIVE, name = context.getString(tv.own.owntv.core.R.string.tuner_antenna_name), remoteId = categoryKey)))
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
                // EPG failures do not prevent watching antenna channels or discard a previous guide.
                try { importGuide(source.id, input, station, now) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { android.util.Log.w("TunerSource", "EPG unavailable for channel ${station.id}: ${e.javaClass.simpleName}") }
            }
        }
    }

    private suspend fun readStations(input: String): List<Station> {
        val columns = arrayOf(TvContract.Channels._ID, TvContract.Channels.COLUMN_DISPLAY_NAME,
            TvContract.Channels.COLUMN_DISPLAY_NUMBER, TvContract.Channels.COLUMN_SERVICE_TYPE)
        val stations = ArrayList<Station>()
        context.contentResolver.query(TvContract.buildChannelsUriForInput(input), columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                currentCoroutineContext().ensureActive()
                if (c.getString(3) == TvContract.Channels.SERVICE_TYPE_AUDIO) continue
                stations += Station(c.getLong(0), c.getString(1) ?: context.getString(tv.own.owntv.core.R.string.tuner_channel_name, c.getLong(0)), c.getString(2)?.toIntOrNull())
            }
        }
        return stations
    }

    suspend fun refreshGuide(source: SourceEntity, storeId: Long = source.id): Int {
        val input = Uri.parse(source.url).getQueryParameter("input") ?: error(context.getString(tv.own.owntv.core.R.string.tuner_missing))
        val stations = readStations(input)
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
        fun isSource(url: String) = url.startsWith("owntv-tuner://")
        fun sourceUrl(id: String): String = Uri.Builder().scheme("owntv-tuner").authority("input").appendQueryParameter("input", id).build().toString()
        fun discover(context: Context): List<Input> = context.getSystemService(TvInputManager::class.java)?.tvInputList.orEmpty()
            .filter { !it.isPassthroughInput && it.type == TvInputInfo.TYPE_TUNER && it.id.contains("/HW") }
            .map { Input(it.id, it.loadLabel(context).toString()) }
    }
}
