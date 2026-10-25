package tv.own.owntv.features.themes

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import tv.own.owntv.ui.theme.OwnTVColors
import tv.own.owntv.ui.theme.StagePalette

val LocalDownloadedTheme = staticCompositionLocalOf<InstalledTheme?> { null }
fun ThemePackage.color(role: String) = Color(colors.getValue(role))
fun ThemePackage.applyTo(base: OwnTVColors): OwnTVColors {
    val accent = color("accent"); val ink = color("text"); val muted = color("muted")
    return base.copy(isDark = !light,
        background = color("page"), surface = color("surface"),
        surfaceContainerLowest = color("page"), surfaceContainerLow = color("surface"),
        surfaceContainer = color("surface"), surfaceContainerHigh = color("surfaceHigh"),
        surfaceContainerHighest = color("surfaceHigh"), onSurface = ink, onSurfaceVariant = muted,
        outline = color("outline"), outlineVariant = color("outline").copy(alpha = .5f),
        primary = accent, onPrimary = color("onAccent"), primaryContainer = color("surfaceHigh"),
        onPrimaryContainer = ink, secondary = color("secondary"), onSecondary = color("page"),
        secondaryContainer = color("surfaceHigh"), onSecondaryContainer = ink,
        tertiary = color("tertiary"), onTertiary = color("page"), tertiaryContainer = color("surfaceHigh"),
        onTertiaryContainer = ink, focusBorder = color("focus"), focusGlow = color("focus").copy(alpha = .25f),
        accentOnVideo = if (light) Color(0xFF75CFFF) else accent,
        onAccentOnVideo = if (light) Color(0xFF082744) else color("onAccent"))
}
fun ThemePackage.stagePalette(): StagePalette {
    val ink = color("text"); val muted = color("muted")
    return (if (light) StagePalette.Light else StagePalette.Dark).copy(
        text = ink, muted = muted, dim = muted, itemText = ink, menuItemText = ink, tagText = muted,
        ink = ink, page = color("page"), glassTint = color("surface"), glassOff = color("surface"),
        body = muted, navText = muted, fxText = ink, fxLine = muted, wash = color("page"),
        segmentOn = color("surfaceHigh"), knobOff = muted, keycap = color("surfaceHigh"))
}
