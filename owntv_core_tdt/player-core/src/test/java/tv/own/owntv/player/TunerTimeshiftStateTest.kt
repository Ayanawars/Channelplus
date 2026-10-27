package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

class TunerTimeshiftStateTest {
    private val now = 1_700_000_000_000L
    private val buffer = TunerTimeshiftState(true, now - 120_000, now - 30_000)

    @Test fun seekClampsToRealBufferAndNeverBeyondLive() {
        assertEquals(now - 60_000, buffer.seekTarget(30, now))
        assertEquals(now - 120_000, buffer.seekTarget(Int.MAX_VALUE, now))
        assertEquals(now, buffer.seekTarget(Int.MIN_VALUE, now))
    }
    @Test fun noSeekBeforeTheTunerSuppliesValidBounds() {
        assertNull(TunerTimeshiftState(available = true).seekTarget(30, now))
        assertNull(buffer.copy(available = false).seekTarget(30, now))
        assertNull(buffer.copy(startMs = now + 1).seekTarget(30, now))
        assertEquals(120, buffer.depthSec(now))
    }
    @Test fun pauseAtLiveEdgeImmediatelyCountsAsTimeshiftAndDelayGrows() {
        val paused = buffer.copy(currentMs = now, paused = true)
        assertEquals(1, paused.offsetSec(now))
        assertEquals(60, paused.offsetSec(now + 60_000))
        assertNull(paused.copy(paused = false).offsetSec(now))
    }
    @Test fun nativePositionTrackingLatencyDoesNotShowFalseRewind() {
        assertNull(buffer.copy(currentMs = now - 1_000).offsetSec(now))
        assertEquals(30, buffer.offsetSec(now))
    }
    @Test fun bufferEvictionClampsDisplayedDelayToWhatRemains() {
        assertEquals(10, buffer.copy(startMs = now - 10_000).offsetSec(now))
        assertEquals(now - 10_000, buffer.copy(startMs = now - 10_000).seekTarget(30, now))
    }
    @Test fun timelineUsesNativeBufferLengthAndDefaultsRemainTwoHours() {
        assertEquals(0.5f, offsetFrac(30, 60), 0.0001f)
        assertEquals(0f, offsetFrac(120, 60), 0.0001f)
        assertEquals(0.5f, offsetFrac(3600), 0.0001f)
        val programme = LiveProgramme(now - 30_000, now, "Show")
        assertEquals(0.5f, liveTicks(listOf(programme), now, 60).single().startFrac, 0.0001f)
    }
}
