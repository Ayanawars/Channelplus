package tv.own.owntv.player

import android.media.tv.TvContract
import android.media.tv.TvView
import android.media.tv.TvInputManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.util.Log
import tv.own.owntv.core.model.TifChannelUri

/** Owns a TV Input session, not a decoder or an HTTP connection. All calls run on Main. */
internal class TifPlayback(private val context: android.content.Context, private val state: (Boolean, String?) -> Unit,
    private val onTimeshift: (TunerTimeshiftState) -> Unit = {},
) {
    companion object {
        private const val TAG = "StayonTimeshift"
        private const val POSITION_LOG_INTERVAL_MS = 15_000L
        private var active: TifPlayback? = null
    }
    private var view: TvView? = null
    private var channel: TifChannelUri? = null
    private var volume = 1f
    private var modeRequested = false
    private var shiftStatus = TvInputManager.TIME_SHIFT_STATUS_UNKNOWN
    private var loggedCurrentPosition = false
    private var lastPositionLogElapsedMs = 0L

    private fun statusName(status: Int): String = when (status) {
        TvInputManager.TIME_SHIFT_STATUS_AVAILABLE -> "AVAILABLE"
        TvInputManager.TIME_SHIFT_STATUS_UNAVAILABLE -> "UNAVAILABLE"
        TvInputManager.TIME_SHIFT_STATUS_UNSUPPORTED -> "UNSUPPORTED"
        TvInputManager.TIME_SHIFT_STATUS_UNKNOWN -> "UNKNOWN"
        else -> "OTHER"
    }
    private val _timeshift = MutableStateFlow(TunerTimeshiftState())
    val timeshift = _timeshift.asStateFlow()
    private fun updateTimeshift(value: TunerTimeshiftState) {
        _timeshift.value = value
        onTimeshift(value)
    }
    private fun resetTimeshift(reason: String) {
        Log.i(TAG, "reset reason=$reason status=${statusName(shiftStatus)} available=${_timeshift.value.available}")
        loggedCurrentPosition = false
        lastPositionLogElapsedMs = 0L
        modeRequested = false
        shiftStatus = TvInputManager.TIME_SHIFT_STATUS_UNKNOWN
        updateTimeshift(TunerTimeshiftState())
    }
    fun attach(next: TvView) {
        if (view === next) return
        Log.i(TAG, "attach sdk=${Build.VERSION.SDK_INT} replacingView=${view != null}")
        view?.setCallback(null)
        view?.setTimeShiftPositionCallback(null)
        view?.reset()
        state(false, null)
        resetTimeshift("attach")
        view = next
        next.setCallback(object : TvView.TvInputCallback() {
            override fun onTimeShiftStatusChanged(inputId: String, status: Int) {
                if (view !== next || channel?.inputId != inputId) {
                    Log.i(TAG, "status_ignored staleSession=true status=${statusName(status)}")
                    return
                }
                Log.i(TAG, "status input=$inputId value=${statusName(status)}($status)")
                shiftStatus = status
                updateTimeshift(if (status == TvInputManager.TIME_SHIFT_STATUS_AVAILABLE)
                    _timeshift.value.copy(available = true) else TunerTimeshiftState())
            }
            override fun onVideoAvailable(inputId: String) {
                if (view !== next || channel?.inputId != inputId) return
                Log.i(TAG, "video_available input=$inputId sdk=${Build.VERSION.SDK_INT} status=${statusName(shiftStatus)}")
                if (!modeRequested && Build.VERSION.SDK_INT >= 34) {
                    modeRequested = true
                    runCatching { next.timeShiftSetMode(TvInputManager.TIME_SHIFT_MODE_AUTO) }
                        .onSuccess { Log.i(TAG, "mode_auto dispatched") }
                        .onFailure { Log.w(TAG, "mode_auto rejected type=${it.javaClass.simpleName}") }
                } else if (Build.VERSION.SDK_INT < 34) {
                    Log.i(TAG, "mode_auto not_requested api_below_34=true")
                }
                state(true, null)
            }
            override fun onVideoUnavailable(inputId: String, reason: Int) {
                if (view === next && channel?.inputId == inputId) {
                    Log.w(TAG, "video_unavailable input=$inputId reason=$reason")
                    val failure = when (reason) {
                        android.media.tv.TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL,
                        android.media.tv.TvInputManager.VIDEO_UNAVAILABLE_REASON_NOT_CONNECTED -> context.getString(tv.own.owntv.core.R.string.tuner_signal_missing)
                        else -> null
                    }
                    state(false, failure)
                }
            }
            override fun onConnectionFailed(inputId: String) = fail(next, inputId, context.getString(tv.own.owntv.core.R.string.tuner_connection_failed))
            override fun onDisconnected(inputId: String) = fail(next, inputId, context.getString(tv.own.owntv.core.R.string.tuner_disconnected))
            override fun onContentBlocked(inputId: String, rating: android.media.tv.TvContentRating) =
                fail(next, inputId, context.getString(tv.own.owntv.core.R.string.tuner_parental_block))
        })
        next.setTimeShiftPositionCallback(object : TvView.TimeShiftPositionCallback() {
            override fun onTimeShiftStartPositionChanged(inputId: String, timeMs: Long) {
                if (view !== next || channel?.inputId != inputId) return
                val start = timeMs.takeIf { it > 0 && it <= System.currentTimeMillis() }
                Log.i(TAG, "start_position rawMs=$timeMs valid=${start != null} status=${statusName(shiftStatus)}")
                val state = _timeshift.value
                updateTimeshift(state.copy(
                    startMs = start,
                    available = state.available || (start != null && shiftStatus == TvInputManager.TIME_SHIFT_STATUS_UNKNOWN),
                ))
            }
            override fun onTimeShiftCurrentPositionChanged(inputId: String, timeMs: Long) {
                if (view !== next || channel?.inputId != inputId) return
                val elapsed = SystemClock.elapsedRealtime()
                if (!loggedCurrentPosition || elapsed - lastPositionLogElapsedMs >= POSITION_LOG_INTERVAL_MS) {
                    Log.i(TAG, "current_position rawMs=$timeMs valid=${timeMs >= 0} status=${statusName(shiftStatus)}")
                    loggedCurrentPosition = true
                    lastPositionLogElapsedMs = elapsed
                }
                updateTimeshift(_timeshift.value.copy(currentMs = timeMs.takeIf { it >= 0 }))
            }
        })
        tune()
    }
    private fun fail(owner: TvView, input: String, message: String) {
        if (view === owner && channel?.inputId == input) {
            Log.w(TAG, "session_failure input=$input message=$message")
            resetTimeshift("session_failure")
            state(false, message)
        }
    }
    fun detach(old: TvView) {
        if (view !== old) return
        Log.i(TAG, "detach")
        view = null
        old.setTimeShiftPositionCallback(null)
        resetTimeshift("detach")
        old.setCallback(null)
        old.reset()
        state(false, null)
        if (active === this) active = null
    }
    fun play(next: TifChannelUri) { resetTimeshift("channel_change"); channel = next; state(false, null); tune() }
    fun setVolume(value: Float) { volume = value.coerceIn(0f, 1f); view?.setStreamVolume(volume) }
    fun retry() = tune()
    /** Native commands preserve the tuner session and its rolling buffer. */
    fun togglePause(): Boolean {
        val target = view ?: run { Log.w(TAG, "pause blocked=no_view"); return false }
        val before = _timeshift.value
        if (!before.available) {
            Log.w(TAG, "pause blocked=status_not_available status=${statusName(shiftStatus)}")
            return false
        }
        Log.i(TAG, "pause request=${if (before.paused) "resume" else "pause"} bufferStart=${before.startMs != null}")
        return command("pause_toggle") {
            if (before.paused) target.timeShiftResume() else target.timeShiftPause()
            updateTimeshift(before.copy(paused = !before.paused,
                currentMs = before.currentMs ?: System.currentTimeMillis()))
        }
    }
    fun seekBy(deltaSec: Int): Boolean {
        val target = view ?: run { Log.w(TAG, "seek blocked=no_view"); return false }
        val position = _timeshift.value.seekTarget(deltaSec, System.currentTimeMillis()) ?: run {
            Log.w(TAG, "seek blocked=buffer_unavailable deltaSec=$deltaSec status=${statusName(shiftStatus)}")
            return false
        }
        Log.i(TAG, "seek request deltaSec=$deltaSec targetMs=$position")
        return command("seek") {
            target.timeShiftSeekTo(position)
            updateTimeshift(_timeshift.value.copy(currentMs = position))
        }
    }
    fun goLive(): Boolean {
        val target = view ?: run { Log.w(TAG, "go_live blocked=no_view"); return false }
        val now = System.currentTimeMillis()
        if (!_timeshift.value.canSeek(now)) {
            Log.w(TAG, "go_live blocked=buffer_unavailable status=${statusName(shiftStatus)}")
            return false
        }
        Log.i(TAG, "go_live request liveMs=$now")
        return command("go_live") {
            target.timeShiftSeekTo(now)
            target.timeShiftResume()
            updateTimeshift(_timeshift.value.copy(paused = false, currentMs = now))
        }
    }
    private inline fun command(operation: String, action: () -> Unit): Boolean = try {
        action()
        Log.i(TAG, "command operation=$operation result=dispatched")
        true
    } catch (e: Exception) {
        Log.e(TAG, "command operation=$operation result=exception type=${e.javaClass.simpleName}", e)
        // A vendor may advertise support but reject a command. Keep watching the channel.
        resetTimeshift("command_exception"); false
    }
    private fun tune() {
        val request = channel ?: return
        val target = view ?: return
        if (active != null && active !== this) {
            Log.w(TAG, "tune blocked=other_session_in_use")
            state(false, context.getString(tv.own.owntv.core.R.string.tuner_in_use))
            return
        }
        active = this
        resetTimeshift("tune")
        Log.i(TAG, "tune input=${request.inputId} sdk=${Build.VERSION.SDK_INT}")
        try {
            target.setStreamVolume(volume)
            target.tune(request.inputId, TvContract.buildChannelUri(request.channelId), Bundle())
            Log.i(TAG, "tune result=dispatched")
        } catch (e: Exception) {
            Log.e(TAG, "tune result=exception type=${e.javaClass.simpleName}", e)
            state(false, context.getString(tv.own.owntv.core.R.string.tuner_tune_failed, e.javaClass.simpleName))
        }
    }
    fun stop() { resetTimeshift("stop"); channel = null; view?.reset(); if (active === this) active = null }
}
