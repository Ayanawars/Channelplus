package tv.own.owntv.core.metadata

/** Only explicit feed classifications; never infer warnings from a plot or a genre. */
object SpanishProgrammeBadges {
    private fun normalized(raw: String) = java.text.Normalizer.normalize(raw.lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").trim()

    fun age(raw: String?): String? {
        val text = raw?.let(::normalized) ?: return null
        if (Regex("\\b(fsk|tv-ma|tv-14|tv-pg|pg-13|nc-17|mpaa|bbfc)\\b").containsMatchIn(text)) return null
        if (Regex("\\b(tp|todos los publicos|apta para todos)\\b").containsMatchIn(text)) return "tp"
        if (text == "infantil") return "infantil"
        if (text == "x") return "x"
        return Regex("(?:^|\\s|[+,(;])(?:\\+)?(0|7|12|16|18)(?:\\+)?(?:$|\\s|[),;])").find(text)?.groupValues?.get(1)
    }

    fun descriptors(values: List<String>): List<String> {
        val terms = values.flatMap { normalized(it).split(Regex("[;,|\\u001f]")) }.map { it.trim() }.toSet()
        val descriptors = linkedMapOf(
            "violencia" to setOf("violencia", "violence"),
            "sexo" to setOf("sexo", "sex", "contenido sexual"),
            "miedo-o-angustia" to setOf("miedo", "angustia", "miedo o angustia", "fear"),
            "lenguaje" to setOf("lenguaje", "lenguaje inapropiado", "lenguaje soez", "language"),
            "drogas-y-sustancias-toxicas" to setOf("drogas", "drogas y sustancias toxicas", "drugs"),
            "discriminacion" to setOf("discriminacion", "discrimination"),
            "conductas-imitables" to setOf("conductas imitables", "imitable behaviour"),
        )
        return descriptors.filterValues { names -> names.any { it in terms } }.keys.toList()
    }
}
