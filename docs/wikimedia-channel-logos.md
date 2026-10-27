# Wikimedia Commons channel logos

Appearance → Channel logos (picons) now offers **Wikimedia (SVG)** alongside Original,
Transparent and Color. Wikimedia is the default when no style has been saved. Explicit existing selections are preserved. The Commons pack is independent
of the Jungle Team importer and does not replace source/EPG logo URLs in the database.

The selection contains 79 original SVG files and 146 explicit channel names/aliases, covering
national, regional and selected pay-TV channels. Quality suffixes use the existing resolver;
exact UHD/HD entries take precedence. Numbered feeds and +1 editions are never matched by
substring. Channels without a matching Commons SVG keep their source/EPG logo.

Assets live in `owntv_core_tdt/core/src/main/assets/channel-logos/wikimedia/`. Every SVG is
bundled byte-for-byte unchanged. `SOURCES.json` retains the original file page, image URL,
author, credit, license information, upload timestamp and content hashes. `ATTRIBUTION.txt`
provides human-readable per-file credits. Commons descriptions include historical alternatives;
the selection is explicit rather than automatically taking a filename with the largest year.
Some SVG containers embed PNGs; those were avoided in favor of actual vector alternatives.

Coil's SVG extension uses the same pinned Coil version as the app and is registered in the
shared ImageLoader. Artwork preserves its aspect ratio and transparency. Coil 3.6.0 renders
SVGs to bitmaps by default, so ChannelLogoTile's existing contrast check remains effective.
The installed app makes no Commons network requests for these logos.

Regenerate or validate:

```bash
python3 tools/channelplus/import_wikimedia_logos.py
python3 tools/channelplus/import_wikimedia_logos.py --offline
```

The editable selection is `tools/channelplus/wikimedia_logos.json`. For a retry, an optional
`--metadata-cache /absolute/path.json` reuses explicitly saved Commons metadata. Omit it to
fetch fresh metadata after changing the selection. The importer validates SHA-1 against
Commons and records SHA-256 for each bundled file, rejects active/external SVG resources,
and preserves the original bytes.

Validation: offline regeneration, all 79 SVGs rendered with librsvg, catalog/asset hashes,
explicit alias mapping, resource XML, app/core translation inventories and whitespace checks.
Regression tests cover both the existing PNG packs and the new SVG catalog. This integration is included in the 5.3.1 update alongside live artwork retention and the larger, frameless player channel logo. Validation on the television follows installation.
