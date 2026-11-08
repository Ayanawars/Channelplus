package tv.own.owntv.features.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * STAYON+ EPG artwork is an optional enhancement of any XMLTV guide, not a special
 * case for one provider. Store globally rather than in the legacy profiles model.
 *
 * Only controls display, never alters the guide's programme data or removes cache.
 */
object EpgArtworkPreferences {
    private const val STORE = "stayon_epg_artwork"
    private const val ENABLED = "show_programme_artwork"
    private val setting = MutableStateFlow(true)
    private var loaded = false

    @Synchronized
    fun observe(context: Context): StateFlow<Boolean> {
        if (!loaded) {
            setting.value = context.applicationContext
                .getSharedPreferences(STORE, Context.MODE_PRIVATE).getBoolean(ENABLED, true)
            loaded = true
        }
        return setting
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED, enabled).apply()
        setting.value = enabled
    }
}
