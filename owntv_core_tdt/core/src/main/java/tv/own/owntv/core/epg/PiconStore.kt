package tv.own.owntv.core.epg

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tv.own.owntv.core.settings.SettingsRepository

/** Optional display override. Provider URLs in the database are never changed. Assets are bundled,
 * so switching style and watching offline requires no logo downloads or extra image cache. */
object PiconStore {
    private var style by mutableStateOf(PiconStyle.ORIGINAL)
    private var catalog by mutableStateOf<PiconCatalog?>(null)

    fun start(context: Context, scope: CoroutineScope, settings: SettingsRepository) {
        scope.launch {
            settings.piconStyle.collectLatest { selected ->
                if (selected != PiconStyle.ORIGINAL && catalog == null) {
                    catalog = runCatching {
                        context.assets.open("picons/catalog.tsv").bufferedReader().use { reader ->
                            PiconCatalog.fromTsv(reader.lineSequence())
                        }
                    }.getOrNull()
                }
                style = selected
            }
        }
    }

    fun iconFor(name: String): String? = catalog?.iconFor(name, style)
}
