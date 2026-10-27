package tv.own.owntv.core.metadata

/** TMDB ratings and TheTVDB artwork retain separate provenance. */
data class ExternalTitleMetadata(
    val tmdbId: Int,
    val type: MetadataType,
    val title: String,
    val tvdbId: Int? = null,
    val tmdbRating: Double? = null,
    val tvdbOverview: String? = null,
    val tvdbArtwork: String? = null,
    val tvdbUrl: String? = null,
    val tvdbLoaded: Boolean = false,
    val posterUrl: String? = null,
    val logoUrl: String? = null,
    val overview: String? = null,
)

object ExternalMetadataRules {
    fun rating(value: String?): Double? = value?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 && it <= 10.0 }

    /** Live titles need a stricter match than a user-selected VOD item, especially across remakes. */
    fun choose(title: String, year: Int?, hits: List<MetadataSearchResult>): MetadataSearchResult? {
        val ranked = hits.distinctBy { it.type to it.tmdbId }.mapNotNull { hit ->
            if (year != null && hit.year != null && kotlin.math.abs(year - hit.year) > 1) return@mapNotNull null
            // A matching year alone cannot promote a partial title into a confident identity.
            if (TitleMatchScorer.score(title, null, hit.title, hit.originalTitle, null) < 0.9) return@mapNotNull null
            val score = TitleMatchScorer.score(title, year, hit.title, hit.originalTitle, hit.year)
            if (score < 0.9) null else hit to score
        }.sortedByDescending { it.second }
        val first = ranked.firstOrNull() ?: return null
        // Same title with no year, or equally good movie/show matches: keep the guide rather than guess.
        if (ranked.drop(1).any { first.second - it.second < 0.1 }) return null
        return first.first
    }

    fun liveTitle(raw: String): String = raw.trim()
        .replace(Regex("(?i)^(?:cine|pel[ií]cula|cinema|movie)\\s*:\\s*"), "")
        .replace(Regex("(?i)\\s+(?:[ST]\\d+(?:\\s*[E:]\\s*\\d+)?|(?:cap[ií]tulo|episodio)\\s*\\d+).*$"), "")
        .trim()
}
