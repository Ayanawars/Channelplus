package tv.own.owntv.core.epg

import kotlinx.coroutines.flow.first
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository

/**
 * Uses one shared candidate pool for TDT and IPTV. The provider tvg-id is already used directly
 * when it carries programmes; this only fills remaining channels with confident name matches.
 * Never replaces a user's manual assignment, including one written while a sync was running.
 */
class EpgAutoAssignmentService(
    private val settings: SettingsRepository,
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val customization: CustomizationStore,
    private val sourceStore: EpgSourceStore,
) {
    data class Result(
        val applied: Int,
        val suggested: Int,
        val guideAvailable: Boolean,
    )

    suspend fun assign(): Result {
        val profileId = settings.activeProfileId.first()
        if (profileId < 0) return Result(0, 0, false)
        // The Guide's source picker is a viewing filter, not an EPG mapping constraint.
        // Match every imported live source, including TDT, even if another playlist is selected.
        val channelSourceIds = sourceDao.observeForProfile(profileId).first()
            .filter { it.syncLive }
            .map { it.id }
        if (channelSourceIds.isEmpty()) return Result(0, 0, false)

        val current = customization.observe(profileId, MediaType.LIVE).first()
        val outcome = EpgAutoMatcher(channelDao, GuideCandidates(epgDao))
            .run(current, channelSourceIds, priorities = sourceStore.priorities.first())
        if (outcome.applied.isEmpty()) {
            return Result(0, outcome.review.size, outcome.hadCandidates)
        }

        var written = 0
        customization.update(profileId, MediaType.LIVE) { latest ->
            // Keep overrides made while the matching scan was running, and avoid needless writes.
            val safe = outcome.applied.filter { (key, _) -> key !in latest.epgMatches }.toMap()
            written = safe.size
            if (safe.isEmpty()) latest else latest.copy(epgMatches = latest.epgMatches + safe)
        }
        return Result(written, outcome.review.size, outcome.hadCandidates)
    }
}
