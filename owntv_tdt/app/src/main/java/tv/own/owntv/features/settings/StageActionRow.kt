package tv.own.owntv.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.stageFocusLook
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** `.tool.box` on a focused row ("▶ Resume", "✕ Remove"): 44 high, 16/700. */
data class RowAction(val text: String, val icon: OwnTVIcon, val onClick: () -> Unit)

/**
 * A `.dl` row (History, paired devices, the error log): radius 24, padding 22, gap 24. The row itself
 * holds focus and takes OK; its [actions] show only while it is focused, to its right, so ▶ reaches them
 * and ▲ ▼ move between rows. [extra] draws under the line (a progress bar, the raw error).
 */
@Composable
internal fun StageActionRow(
    height: Dp,
    focus: FocusRequester?,
    onClick: () -> Unit,
    leading: @Composable (focused: Boolean) -> Unit,
    title: String,
    line: String?,
    actions: List<RowAction> = emptyList(),
    titleSize: Int = 24,
    trailing: (@Composable () -> Unit)? = null,
    extra: @Composable (focused: Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var hasFocus by remember { mutableStateOf(false) }
    Box(
        modifier
            .padding(bottom = 10.mpx)
            .fillMaxWidth()
            .heightIn(min = height)
            .onFocusChanged { hasFocus = it.hasFocus }
            .then(if (hasFocus) Modifier.stageFocusLook(StageFocus.FX, 24.mpx) else Modifier)
            .focusGroup()
            .padding(horizontal = 22.mpx, vertical = 10.mpx),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.mpx), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
                    .clickable(interactionSource = null, indication = null, onClick = onClick),
                horizontalArrangement = Arrangement.spacedBy(24.mpx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading(hasFocus)
                Column(Modifier.weight(1f)) {
                    Text(title, style = stageText(titleSize, 700), color = if (hasFocus) StageColors.FxText else StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (line != null) Text(line, style = stageText(17, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.mpx))
                    extra(hasFocus)
                }
                trailing?.invoke()
            }
            if (hasFocus) actions.forEach { a ->
                StageSurface(
                    onClick = a.onClick, radius = 15.mpx, modifier = Modifier.height(44.mpx),
                    idle = Modifier.background(StageColors.ControlFill, RoundedCornerShape(15.mpx)),
                ) { focused ->
                    val c = if (focused) stageAccent.onAccent else StageColors.Text
                    Row(Modifier.padding(horizontal = 16.mpx), horizontalArrangement = Arrangement.spacedBy(9.mpx), verticalAlignment = Alignment.CenterVertically) {
                        OwnTVIcon(a.icon, c, Modifier.size(18.mpx), filled = a.icon == OwnTVIcon.PLAY)
                        Text(a.text, style = stageText(16, 700), color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
