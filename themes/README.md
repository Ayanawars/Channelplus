# Channel + downloadable themes (schema 1)

These files are served separately from the Android APK. Nothing in this directory is an Android asset.
Open **Settings → Appearance → Downloadable themes**. Refresh the catalogue, download a theme, then
apply it. Installed themes and the cached catalogue work offline. The original theme remains available;
applying a downloadable theme does not overwrite the user's appearance preferences. Deleting an active
theme restores those preferences first. Themes are device-wide, like the existing appearance controls.

## Publishing

The app reads `themes/catalog.json` on the `codex/complete-import-channel-review` branch of
`Ayanawars/Channelplus`. Keep this endpoint available until a later app version migrates it.
Each entry includes a stable safe ID, display name, version, light/dark flag, three preview swatches,
package URL, exact byte count, SHA-256, and total download bytes. Increase the theme version for an update.
Put new theme versions under a new directory and update the catalogue only after the assets exist.
The catalogue can add themes without an APK update. The catalogue UI also shows installed themes that
have since been removed from the online catalogue.

A package is a small JSON document with schema, identity, eleven required opaque colour roles and an
optional wallpaper URL, byte count and SHA-256. Only HTTPS assets from this repository on
raw.githubusercontent.com are accepted; redirects are rejected. JSON packages are capped at 32 KiB,
wallpapers at 4 MiB and 3840 × 2160 pixels. Text and primary-control contrast are checked before installation.
Downloads are verified before being atomically activated, and images are decoded off the UI thread.
No ZIP extraction, executable code or remote layout is supported. Failed downloads preserve the previous
theme; unused image files are cleaned up. SharedPreferences stores only the selected ID, while all
packages and images stay in private internal files (not evictable cache).

Cyberpunk includes an original AI-generated city wallpaper created for Channel +. OLED Esmeralda uses
pure black with turquoise and emerald accents; Pastel uses sky blue, butter yellow and lavender.
Colour palettes and wallpaper are distributed under GPL-3.0-or-later alongside Channel +.
Existing OwnTV / OwnTV Core attribution and font licences are unchanged.

## Validation

`ThemePackageTest` checks catalogue/package identity, byte counts, SHA-256 and all shipped palettes,
and rejects unsafe paths, unsupported schemas, unreadable colours, untrusted hosts, oversized assets,
invalid checksums and duplicate IDs. CI runs these with the app unit tests and assembles the standard APK.
TV validation still needs a remote control: browse the catalogue, download/apply every theme, restart
without internet, delete active/inactive themes, restore original appearance and check settings/player
contrast and focus at the user's preferred UI/font zoom.
