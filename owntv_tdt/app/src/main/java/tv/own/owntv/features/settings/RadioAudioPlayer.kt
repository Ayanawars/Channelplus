package tv.own.owntv.features.settings

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lightweight radio-only audio engine. Does not replace the TDT/LOVEStv video engines.
 * Streams play inside STAYON+, without launching a separate Android app.
 */
object RadioAudioPlayer {
    private var player: MediaPlayer? = null
    private val _playingStationId = MutableStateFlow<String?>(null)
    val playingStationId: StateFlow<String?> = _playingStationId
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    @Synchronized
    fun stop() {
        val old = player
        player = null
        _playingStationId.value = null
        runCatching { old?.stop() }
        runCatching { old?.release() }
    }

    @Synchronized
    fun play(station: StayonRadioStation) {
        stop()
        _error.value = null
        require(StayonRadioLibrary.isStreamUrl(station.url))
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            mp.setDataSource(station.url)
            mp.setOnPreparedListener {
                // Never start a stale stream after the user changes station or leaves.
                if (player === it) runCatching { it.start() }.onFailure { e ->
                    _error.value = e.message
                    stop()
                }
            }
            mp.setOnErrorListener { who, what, extra ->
                if (player === who) {
                    _error.value = listOf(what, extra).joinToString("/")
                    stop()
                }
                true
            }
            mp.setOnCompletionListener { if (player === it) stop() }
            player = mp
            _playingStationId.value = station.id
            mp.prepareAsync()
        } catch (e: Exception) {
            _error.value = e.localizedMessage ?: e.javaClass.simpleName
            stop()
        }
    }
}
