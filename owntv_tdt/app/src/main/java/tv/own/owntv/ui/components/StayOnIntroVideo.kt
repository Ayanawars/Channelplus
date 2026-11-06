package tv.own.owntv.ui.components

import android.content.ContentResolver
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import tv.own.owntv.R

/**
 * Approved 5-second STAYON+ MP4 with its original audio.
 * Rendered only for a cold launcher start, above the existing shell.
 * No interaction with TV playback engines, TIF or HbbTV.
 */
@Composable
fun StayOnIntroVideo(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val finish by rememberUpdatedState(onFinished)
    AndroidView(
        modifier = modifier.fillMaxSize().background(Color.Black),
        factory = { context ->
            VideoView(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                val uri = Uri.Builder()
                    .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                    .authority(context.packageName)
                    .appendPath(R.raw.stayon_intro.toString())
                    .build()
                setOnCompletionListener { finish() }
                setOnErrorListener { _, _, _ ->
                    finish() // never strand the user on a black screen if decoding fails
                    true
                }
                setOnPreparedListener { mediaPlayer ->
                    mediaPlayer.isLooping = false
                    mediaPlayer.setVolume(1f, 1f)
                    start()
                }
                setVideoURI(uri)
            }
        },
        onRelease = { it.stopPlayback() },
    )
}
