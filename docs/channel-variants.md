# Local channel variants (not published)

TDT and IPTV versions of a station are grouped without changing stored channels, source credentials, favorites or custom-category memberships. Live lists, the guide and CH+/CH− use grouped stations. Each rail retains its own membership/order. Playback resolves alternatives across this profile’s enabled, visible sources.

Automatic order is all TDT sources in descending quality, then all IPTV sources in descending quality. IPTV-only stations start directly on IPTV. The gear menu offers Source / Quality, Automatic, and individual source versions. A manual pick lasts until the next channel selection. Source errors are allowed three seconds for existing engine recovery; a TDT input stuck without picture or error gets fifteen seconds. Each version is attempted once per tune; changing to a new station cancels the prior watcher. Parental blocking does not trigger source fallback. Switching source is hidden during rewind/catch-up.

Tuner synchronization imports the signal format supplied by Android TV into an optional height parameter of the existing TIF URI. Existing URIs remain valid; refresh the antenna source to populate resolution for older imported channels. Unknown quality remains unspecified. No database migration is required. IPTV quality follows the provider name and is not independently probed.

Matching normalizes spaces, case, accents, Spanish provider prefixes and quality/source suffixes. Country and regional names (+1/Canarias etc.) remain distinct. Ambiguous names are not manually mapped in this iteration. Guide ids alone do not merge stations. The playback EPG can fall back to another version’s guide when its own is missing.

For correctness across distant duplicate provider rows, grouped paging first loads the selected rail’s underlying Room pages, then paginates its station snapshot. Room invalidation rebuilds it. Large provider lists need performance verification on the TV; active source grouping is indexed off the main thread.

## Validation in this environment

- Resource XML parsing: passed for both new resource files.
- git diff --check: passed.
- Added unit cases: source/quality order, IPTV-only, regional/country separation, repeated collapse, tuner format, cross-page grouping, hidden alternatives, Room invalidation, backwards paging and source-independent zapping.
- Gradle unit tests/build: NOT RUN. Wrapper distribution gradle-9.7.1 is absent; services.gradle.org is unreachable from this environment. No APK was produced.
- Existing i18n literal verification still reports the pre-existing literal “picons” in SettingsScreen.kt; no new literal violation was reported in this change.
- Device checks remain: TDT input session release into IPTV, no-signal failover, source popup D-pad focus, EPG/logo continuity and large-list load time.

Working base: 4261ebd. No commit, push, remote write or release was performed.
