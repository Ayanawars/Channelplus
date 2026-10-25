package tv.own.owntv.player

import android.media.tv.TvContract
import android.media.tv.TvView
import android.os.Bundle
import tv.own.owntv.core.model.TifChannelUri

/** Owns a TV Input session, not a decoder or an HTTP connection. All calls run on Main. */
internal class TifPlayback(private val context: android.content.Context, private val state: (Boolean, String?) -> Unit) {
    companion object { private var active: TifPlayback? = null }
    private var view: TvView? = null
    private var channel: TifChannelUri? = null
    private var volume = 1f
    fun attach(next: TvView) {
        if (view === next) return
        view?.reset()
        view = next
        next.setCallback(object : TvView.TvInputCallback() {
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
        tune()
    }
    private fun fail(owner: TvView, input: String, message: String) {
        if (view === owner && channel?.inputId == input) state(false, message)
    }
    fun detach(old: TvView) {
        if (view !== old) return
        view = null
        old.setCallback(null)
        old.reset()
        if (active === this) active = null
    }
    fun play(next: TifChannelUri) { channel = next; state(false, null); tune() }
    fun setVolume(value: Float) { volume = value.coerceIn(0f, 1f); view?.setStreamVolume(volume) }
    fun retry() = tune()
    private fun tune() {
        val request = channel ?: return
        val target = view ?: return
        if (active != null && active !== this) {
            state(false, context.getString(tv.own.owntv.core.R.string.tuner_in_use))
            return
        }
        active = this
        try {
            target.setStreamVolume(volume)
            target.tune(request.inputId, TvContract.buildChannelUri(request.channelId), Bundle())
        } catch (e: Exception) {
            state(false, context.getString(tv.own.owntv.core.R.string.tuner_tune_failed, e.javaClass.simpleName))
        }
    }
    fun stop() { channel = null; view?.reset(); if (active === this) active = null }
}
