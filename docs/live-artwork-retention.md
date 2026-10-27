# Live artwork retention — 5.3.1

The fullscreen live player owns programme enrichment independently of HUD visibility.
Closing controls no longer cancels the request or resets the result to null. The owner
tracks live versus archive programmes without collecting per-second playback position in
the shell. Programme identity changes clear the displayed result before the next lookup.

A bounded, five-minute session cache restores positive metadata synchronously on reopening.
Its identity includes the title, matching EPG fields, configured providers/language and
active profile. Errors and missing matches do not replace already delivered artwork.
Optional TVDB translation/logo requests no longer delay a usable poster: the repository
publishes TMDB artwork immediately and TVDB artwork as soon as the extended series response
arrives. The final disk cache and its failure retry TTL remain unchanged.

Poster and title-logo downloads start while controls are hidden. Their independent image
requests avoid restarting the poster when the logo arrives. Prefetch sizes fit the existing
bounded Coil memory cache; UI artwork disables crossfade so cached artwork appears directly.

The player channel logo is 72 dp high instead of 32 dp before the user's HUD size scale.
It has a wide fit box, no colored fill, no clipping and no adaptive background plate. The
current selected logo pack is resolved reactively instead of relying on a stale engine
metadata snapshot. Channel OSDs share the same frameless renderer. Source artwork itself is
not edited. Unset logo preferences now use the bundled Wikimedia SVG pack; explicit saved
choices remain intact.

Regression coverage includes session reuse, incremental logo enrichment, profile/config
isolation, cache expiry/LRU bounds, early TVDB poster delivery and preservation when an
optional translation request fails. The device screenshots reported on 5.3.0 are the trigger;
TV runtime behavior must be confirmed after installing the update.
