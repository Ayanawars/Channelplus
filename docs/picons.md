# Channel+ picons

Settings → Appearance → Channel logos (picons) offers Original logos, Wikimedia (SVG), Transparent and Color.
Wikimedia is the default for an unset preference; explicit choices remain unchanged. The selection is persisted and included in settings backups.
The two Jungle Team packs and the separate Wikimedia SVG pack are bundled as assets: neither a network request nor a new APK release
is needed when switching between them. Unmatched names retain the EPG/provider logo.

The display override uses `ChannelEntity.displayLogoUrl`, shared by Live TV, guide, search,
favorites and playback metadata. Home live artwork, sports channel rows and direct-tune feedback
also use it. Channel data, source URLs and recording metadata remain unchanged.

Assets: 280 catalog names/variants, 447 deduplicated PNGs across the two styles (about 13 MiB).
Name matching ignores accents, spacing and quality suffixes, includes common Movistar/Spanish
aliases, and preserves feed numbers and +1. It does not guess by substrings. Coverage is limited
to identifiable channels in the source packs; an Enigma2 service reference without a known name
is deliberately not guessed.

Upstream images are unchanged. Attribution, exact upstream commits, original filenames and
the upstream LICENSE are included under `owntv_core_tdt/core/src/main/assets/picons/`.
To regenerate after updating local upstream checkouts:

```bash
python tools/channelplus/import_picons.py /path/to/Picon-enigma2-Movistar /path/to/Canales-enigma2
```

Validation: PNG decoding, content hashes, catalog paths, ES/EN resource XML, name/alias fixtures,
numbered-feed isolation and whitespace checks passed. The existing PNG integration was included
in the published 5.3.0 build. Both catalog test suites pass (8 JVM tests) with the new SVG style;
the SVG pack ships with 5.3.1.

Wikimedia integration: see [wikimedia-channel-logos.md](wikimedia-channel-logos.md). The SVG pack is independent of the PNG importer and retains its own per-file attribution.
