package tv.own.owntv.player

import android.media.tv.TvContract
import android.media.tv.TvView
import android.media.tv.TvInputManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.os.Bundle
import tv.own.owntv.core.model.TifChannelUri

/** Owns a TV Input session, not a decoder or an HTTP connection. All calls run on Main. */
internal class TifPlayback(private val context: android.content.Context, private val state: (Boolean, String?) -> Unit,
    private val onTimeshift: (TunerTimeshiftState) -> Unit = {},
) {
    companion object { private var active: TifPlayback? = null }
    private var view: TvView? = null
    private var channel: TifChannelUri? = null
    private var volume = 1f
    private val _timeshift = MutableStateFlow(TunerTimeshiftState())
    val timeshift = _timeshift.asStateFlow()
    private fun updateTimeshift(value: TunerTimeshiftState) {
        _timeshift.value = value
        onTimeshift(value)
    }
    private fun resetTimeshift() = updateTimeshift(TunerTimeshiftState())
    fun attach(next: TvView) {
        if (view === next) return
        view?.setCallback(null)
        view?.setTimeShiftPositionCallback(null)
        view?.reset()
        state(false, null)
        resetTimeshift()
        view = next
        next.setCallback(object : TvView.TvInputCallback() {
            override fun onTimeShiftStatusChanged(inputId: String, status: Int) {
                if (view !== next || channel?.inputId != inputId) return
                updateTimeshift(if (status == TvInputManager.TIME_SHIFT_STATUS_AVAILABLE)
                    _timeshift.value.copy(available = true) else TunerTimeshiftState())
            }
            override fun onVideoAvailable(inputId: String) {
                if (view === next && channel?.inputId == inputId) state(true, null)
            }
            override fun onVideoUnavailable(inputId: String, reason: Int) {
                if (view === next && channel?.inputId == inputId) {
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
                if (view === next && channel?.inputId == inputId)
                    updateTimeshift(_timeshift.value.copy(startMs = timeMs.takeIf { it >= 0 }))
            }
            override fun onTimeShiftCurrentPositionChanged(inputId: String, timeMs: Long) {
                if (view === next && channel?.inputId == inputId)
                    updateTimeshift(_timeshift.value.copy(currentMs = timeMs.takeIf { it >= 0 }))
            }
        })
        tune()
    }
    private fun fail(owner: TvView, input: String, message: String) {
        if (view === owner && channel?.inputId == input) {
            resetTimeshift()
            state(false, message)
        }
    }
    fun detach(old: TvView) {
        if (view !== old) return
        view = null
        old.setTimeShiftPositionCallback(null)
        resetTimeshift()
        old.setCallback(null)
        old.reset()
        state(false, null)
        if (active === this) active = null
    }
    fun play(next: TifChannelUri) { resetTimeshift(); channel = next; state(false, null); tune() }
    fun setVolume(value: Float) { volume = value.coerceIn(0f, 1f); view?.setStreamVolume(volume) }
    fun retry() = tune()
    /** Native commands preserve the tuner session and its rolling buffer. */
    fun togglePause(): Boolean {
        val target = view ?: return false
        val before = _timeshift.value
        if (!before.available) return false
        return command {
            if (before.paused) target.timeShiftResume() else target.timeShiftPause()
            updateTimeshift(before.copy(paused = !before.paused,
                currentMs = before.currentMs ?: System.currentTimeMillis()))
        }
    }
    fun seekBy(deltaSec: Int): Boolean {
        val target = view ?: return false
        val position = _timeshift.value.seekTarget(deltaSec, System.currentTimeMillis()) ?: return false
        return command {
            target.timeShiftSeekTo(position)
            updateTimeshift(_timeshift.value.copy(currentMs = position))
        }
    }
    fun goLive(): Boolean {
        val target = view ?: return false
        val now = System.currentTimeMillis()
        if (!_timeshift.value.canSeek(now)) return false
        return command {
            target.timeShiftSeekTo(now)
            target.timeShiftResume()
            updateTimeshift(_timeshift.value.copy(paused = false, currentMs = now))
        }
    }
    private inline fun command(action: () -> Unit): Boolean = try {
        action(); true
    } catch (_: Exception) {
        // A vendor may advertise support but reject a command. Keep watching the channel.
        resetTimeshift(); false
    }
    private fun tune() {
        val request = channel ?: return
        val target = view ?: return
        if (active != null && active !== this) {
            state(false, context.getString(tv.own.owntv.core.R.string.tuner_in_use))
            return
        }
        active = this
        resetTimeshift()
        try {
            target.setStreamVolume(volume)
            target.tune(request.inputId, TvContract.buildChannelUri(request.channelId), Bundle())
        } catch (e: Exception) {
            state(false, context.getString(tv.own.owntv.core.R.string.tuner_tune_failed, e.javaClass.simpleName))
        }
    }
    fun stop() { resetTimeshift(); channel = null; view?.reset(); if (active === this) active = null }
}
