# Settings navigation redesign

Settings now opens directly from More, without the intermediate grid of category tiles.
The category sidebar stays visible on the shared settings root and detail pages. General
is the default entry. Existing group identities remain stable for saved navigation,
search, pinned shortcuts and return focus; only the menu order and displayed names change.

The shared settings page uses a wide list with inline descriptions and current values.
Rows, editable fields and search results use an ivory focus with dark text in dark themes.
Light themes keep their existing accessible accent. Plain help appears in a fixed-height
footer, so moving focus does not change the list viewport. Functional source actions,
editors and dedicated previews keep their own panel when needed.

Remote navigation:

- Up/down moves through categories; OK displays a category.
- Right selects the focused category and enters its options.
- Left from the options returns to the current category (steppers retain left/right editing).
- Back from a detail page returns to its parent, with the opening row restored.
- Back from the root options focuses the category menu; Back there returns to More.
- Search clears its query before leaving; popup editors retain their own Back handler.

The former tile screen and its hard-coded setting counts have been removed. All existing
settings, quick pins, source actions and metadata configuration remain available. The
About page's language shortcut now uses the General group identity instead of index 8.

Validation: translation literal inventory, text overflow policy, EN/ES navigation resources,
resource references and whitespace checks. No APK build or television runtime verification
has been performed for this change. No GitHub push.
