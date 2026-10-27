package tv.own.owntv.ui.metadata

import tv.own.owntv.core.metadata.ExternalTitleMetadata

/** Small session cache: reopening a HUD starts with its artwork, rather than a blank produceState. */
internal class ProgrammeArtworkCache<K : Any>(
    private val capacity: Int = 64,
    private val ttlMs: Long = 5 * 60_000L,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val data: ExternalTitleMetadata, val expires: Long)
    private val entries = LinkedHashMap<K, Entry>(capacity, 0.75f, true)

    @Synchronized fun get(key: K): ExternalTitleMetadata? {
        val entry = entries[key] ?: return null
        if (entry.expires <= now()) { entries.remove(key); return null }
        return entry.data
    }

    @Synchronized fun put(key: K, data: ExternalTitleMetadata) {
        entries[key] = Entry(data, now() + ttlMs)
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }
}
