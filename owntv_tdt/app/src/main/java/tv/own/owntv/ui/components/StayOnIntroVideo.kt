package tv.own.owntv.ui.components

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import tv.own.owntv.R

/** Texture-backed intro participates in Compose's foreground stacking. An opaque
 * VideoView background can cover its separately composed video surface on TVs. */
@Composable
fun StayOnIntroVideo(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val finish by rememberUpdatedState(onFinished)
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = remember(context) { IntroTextureView(context) { finish() } }
    DisposableEffect(view, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) view.finish()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            view.release()
        }
    }
    AndroidView(
        modifier = modifier.fillMaxSize().background(Color.Black),
        factory = { view },
        onRelease = { it.release() },
    )
}

private class IntroTextureView(context: Context, private val onFinished: () -> Unit) :
    TextureView(context), TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var output: Surface? = null
    private var closed = false
    private var videoWidth = 0
    private var videoHeight = 0
    private val timeout = Runnable { finish() }

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        if (closed) return
        postDelayed(timeout, 8_000L)
        try {
            output = Surface(texture)
            val media = MediaPlayer()
            player = media
            media.also {
                media.setSurface(output)
                context.resources.openRawResourceFd(R.raw.stayon_intro).use { fd ->
                    media.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                }
                media.setOnVideoSizeChangedListener { _, w, h ->
                    videoWidth = w
                    videoHeight = h
                    fitVideo()
                }
                media.setOnPreparedListener {
                    if (!closed) {
                        fitVideo()
                        it.isLooping = false
                        it.setVolume(1f, 1f)
                        it.start()
                    }
                }
                media.setOnInfoListener { _, what, _ ->
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                        removeCallbacks(timeout)
                        postDelayed(timeout, media.duration.coerceAtLeast(0) + 2_000L)
                    }
                    false
                }
                media.setOnCompletionListener { finish() }
                media.setOnErrorListener { _, _, _ -> finish(); true }
                media.prepareAsync()
            }
        } catch (_: Exception) {
            finish()
        }
    }

    private fun fitVideo() {
        if (videoWidth <= 0 || videoHeight <= 0 || width <= 0 || height <= 0) return
        val scale = minOf(width.toFloat() / videoWidth, height.toFloat() / videoHeight)
        setTransform(Matrix().apply {
            setScale(videoWidth * scale / width, videoHeight * scale / height, width / 2f, height / 2f)
        })
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = fitVideo()
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        finish()
        return true
    }

    fun finish() {
        if (closed) return
        release()
        onFinished()
    }

    fun release() {
        if (closed) return
        closed = true
        removeCallbacks(timeout)
        player?.apply {
            setOnPreparedListener(null)
            setOnCompletionListener(null)
            setOnErrorListener(null)
            setOnVideoSizeChangedListener(null)
            release()
        }
        player = null
        output?.release()
        output = null
    }
}
