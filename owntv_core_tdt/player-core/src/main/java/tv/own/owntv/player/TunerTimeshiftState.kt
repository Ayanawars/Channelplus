package tv.own.owntv.player

/** Native TV-input buffer positions are epoch milliseconds, never ExoPlayer media positions. */
data class TunerTimeshiftState(
    val available: Boolean = false,
    val startMs: Long? = null,
    val currentMs: Long? = null,
    val paused: Boolean = false,
) {
    fun canSeek(nowMs: Long): Boolean = available && startMs != null && startMs <= nowMs
    fun offsetSec(nowMs: Long): Int? {
        if (!available) return null
        val current = currentMs?.coerceAtLeast(startMs ?: 0L) ?: return if (paused) 1 else null
        val seconds = ((nowMs - current).coerceAtLeast(0) / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return if (paused) seconds.coerceAtLeast(1) else seconds.takeIf { it > 2 }
    }
    fun seekTarget(deltaSec: Int, nowMs: Long): Long? {
        if (!canSeek(nowMs)) return null
        return ((currentMs ?: nowMs) - deltaSec.toLong() * 1000L).coerceIn(requireNotNull(startMs), nowMs)
    }
    fun depthSec(nowMs: Long): Int = if (!canSeek(nowMs)) 0 else
        ((nowMs - requireNotNull(startMs)) / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
