package tv.own.owntv.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester

/** Present only inside Settings; setup and other screens keep their independent layouts. */
class SettingsNavigation(
    val categoryFocus: FocusRequester,
    val contentFocus: FocusRequester,
    val sidebar: @Composable () -> Unit,
)

val LocalSettingsNavigation = staticCompositionLocalOf<SettingsNavigation?> { null }
