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
    private var wikimedia by mutableStateOf<PiconCatalog?>(null)

    fun start(context: Context, scope: CoroutineScope, settings: SettingsRepository) {
        scope.launch {
            settings.piconStyle.collectLatest { selected ->
                if (selected == PiconStyle.WIKIMEDIA && wikimedia == null) {
                    wikimedia = runCatching {
                        context.assets.open("channel-logos/wikimedia/catalog.tsv").bufferedReader().use { reader ->
                            PiconCatalog.fromWikimediaTsv(reader.lineSequence())
                        }
                    }.getOrNull()
                }
                if (selected != PiconStyle.ORIGINAL && selected != PiconStyle.WIKIMEDIA && catalog == null) {
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

    fun iconFor(name: String): String? =
        (if (style == PiconStyle.WIKIMEDIA) wikimedia else catalog)?.iconFor(name, style)
}
