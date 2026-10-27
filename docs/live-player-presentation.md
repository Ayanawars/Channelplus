# Live player presentation — local changes

Channel+ live playback now uses the selected cinematic layout: wordmark and clock at the top, channel identity and state at the bottom, then EPG title, full-width programme progress with start/end times, remaining minutes, synopsis and next emission. Missing guide fields are omitted; channel identity is always retained. IMDb and TheTVDB are not integrated.

Source/quality opens the existing version picker and shows the actual selected source and known quality. One gear opens the existing playback tools, transport and seekable buffer. Programme progress is informational and remains separate from timeshift buffer depth. Archive playback uses its own EPG and watched instant. VOD presentation stays on the existing path.

Focus lands on the gear when revealing the compact live HUD. Opening and closing the menu retains focus on its gear; Back closes tools before hiding the HUD. Tools suspend auto-hide. Existing hidden Left/Right rewind shortcuts reveal the tools and focus the seekable timeline. Channel changes reset the tools. Navigation and native tuner/timeshift behavior still need device validation.

Validation: new XML resources parse; git diff whitespace check passes. The translation inventory reports no newly introduced literals in these player changes; its whole-project check remains blocked by the existing `picons` literal in SettingsScreen.kt. Attempted Kotlin compilation cannot start: Gradle 9.7.1 is absent and the wrapper download fails with Network is unreachable. No APK build or device test is claimed. No GitHub push or release performed.
