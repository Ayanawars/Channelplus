package tv.own.owntv.core.sync

import android.content.Context
import android.media.tv.TvContract
import java.text.Normalizer
import java.util.Locale

/** Local decisions about services on a TV input, shared by the TV and radio importers. */
object TunerRadioClassification {
    private const val STORE = "stayon_tuner_classification"
    private val marks = Regex("\\p{M}+")
    private val radioNames = Regex("""^(?:radio(?:\b|\d)|rne\b|r\s?ne\b|ser\b|cadena (?:ser|100)\b|cope\b|onda cero\b|los\s?40\b|rac\s?[15]\b|kiss fm\b|europa fm\b|rock fm\b|melodia fm\b|dial\b|esradio\b)""")
    private val tvMarker = Regex("""\btv\b""")

    fun suggestRadio(name: String, serviceType: String?): Boolean {
        if (serviceType == TvContract.Channels.SERVICE_TYPE_AUDIO) return true
        val normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(marks, "").trim().lowercase(Locale.ROOT)
        return !tvMarker.containsMatchIn(normalized) && radioNames.containsMatchIn(normalized)
    }

    fun read(context: Context, inputId: String): Map<Long, Boolean> =
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getStringSet(inputId, emptySet()).orEmpty()
            .mapNotNull { item ->
                val id = item.drop(1).toLongOrNull() ?: return@mapNotNull null
                id to (item.first() == 'R')
            }.toMap()

    fun save(context: Context, inputId: String, observed: Set<Long>, radio: Set<Long>) {
        val decisions = read(context, inputId) + observed.associateWith { it in radio }
        val stored = decisions.mapTo(mutableSetOf()) { (id, isRadio) -> (if (isRadio) 'R' else 'T').toString() + id }
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putStringSet(inputId, stored).apply()
    }
}
