# Channel+

Android TV app based on OwnTV (https://github.com/ahXN00/OwnTV), targeting a native TV tuner experience.

## Roadmap

1. Import OwnTV source, retaining original GPL-3.0 license and attribution.
2. Preserve IPTV and existing TDT support and verify both source synchronization paths.
3. Add Select all / Deselect all to IPTV group visibility editor, with explicit save and clear focus for D-pad navigation.
4. Include TDT channels in All channels alongside IPTV, with source labels and deduplication safeguards.
5. Add TDT channel ordering and editing controls.
6. Adjust app name, icon, navigation and launch experience for a built-in tuner feel, without claiming system privileges.
7. Build debug APK and test on TCL Google TV.

## Notes

OwnTV uses Kotlin and Compose for TV, with a separately published OwnTV Core dependency. Verify exact upstream version before modifying. Never commit IPTV credentials, playlists or personal data.
