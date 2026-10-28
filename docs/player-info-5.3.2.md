# Channel+ 5.3.2 · Live information and metadata

- OK reveals the existing programme information for 15 seconds; Back hides it immediately. VOD retains its existing timer. Options and dialogs stay open while being used.
- Down opens the complete playback options as labelled rows; Up on the first row returns to programme information. Source and quality controls remain in Options. The idle information bar has no source chip or gear.
- The channel row shows logo and number. The full channel name remains available until the image loads or if it fails.
- IMDb rectangular SVG and yellow one-decimal score, then age/content SVGs separated by bullets. Year, programme type and genre are on the next line. SVG files come from the supplied ZIPs, with their source notes preserved.
- Trakt is the active metadata provider. TheTVDB supplies artwork and fallback live metadata, independently of Trakt failures. Legacy `tmdbId` field names are retained as cross-reference identifiers for existing catalogue records, not TMDB API calls.
- The IMDb score comes only from Trakt's `/movies|shows/{id}/ratings?extended=all` response, `imdb.rating`. Trakt community scores and TMDB scores are never substituted. Missing scores and unprovided content warnings are omitted.
- The external live cache is versioned to discard old TMDB-sourced data. Credentials are entered under Metadata → Trakt + TheTVDB and remain in the existing encrypted, non-backup store. An existing TheTVDB configuration is preserved. A Trakt application Client ID must be configured to enable that service.

## Validation

The GitHub check runs app/core/player unit tests and assembles the ARM debug APK. Real D-pad focus, subtitle/audio selections, TDT/IPTV playback and logo failure behaviour still require a TV test. CI uses a disposable signing key; it must not be distributed as an update to an existing installation. Production APKs need the original external signing key.
