package tv.own.owntv.features.themes

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ThemeBackdrop(modifier: Modifier = Modifier) {
    val active = LocalDownloadedTheme.current ?: return
    val theme = active.theme
    val bitmap by produceState<ImageBitmap?>(null, active.wallpaper) {
        value = withContext(Dispatchers.IO) { runCatching {
            active.wallpaper?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
        }.getOrNull() }
    }
    Box(modifier.background(theme.color("page"))) {
        bitmap?.let {
            Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(theme.color("page").copy(alpha = .4f)))
        }
        if (theme.light) Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(
            Color(0xFFDAEFFF), Color(0xFFEFF5FF), Color(0xFFFFF5CC)))))
    }
}
