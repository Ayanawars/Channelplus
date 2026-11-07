package tv.own.owntv.features.shell.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

val StageContentTop = 110.mpx

@Composable
internal fun ShellProfileAvatar(initial: String, size: Int = 46, textSize: Int = 20, avatarId: Int = -1, imagePath: String = "") {
    if (avatarId != -1 || imagePath.isNotBlank()) {
        tv.own.owntv.ui.components.OwnTVAvatar(avatarId = avatarId, imagePath = imagePath, modifier = Modifier.size(size.mpx).clip(CircleShape))
        return
    }
    Box(
        Modifier
            .size(size.mpx)
            .background(Brush.linearGradient(listOf(Color(0xFFFFB35C), Color(0xFFFF5F7A))), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = stageText(textSize, 800), color = Color(0xFF2A0D12), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
