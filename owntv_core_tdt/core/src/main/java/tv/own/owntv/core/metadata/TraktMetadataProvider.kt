package tv.own.owntv.core.metadata

import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import tv.own.owntv.core.settings.SettingsRepository

/** Adapter for the existing catalogue cache. Legacy tmdbId fields are cross-reference IDs only. */
class TraktMetadataProvider(
    private val api: ExternalMetadataClient,
    private val store: ExternalMetadataStore,
    private val settings: SettingsRepository,
) : MetadataProvider {
    private suspend fun <T> attempt(block: suspend (ExternalMetadataConfig) -> T): T? {
        val cfg = store.config.value
        if (!cfg.enabled || cfg.traktClientId.isBlank()) return null
        return try { block(cfg) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }

    override suspend fun searchMovie(title: String, year: Int?, includeAdult: Boolean) = search(title, year, MetadataType.MOVIE, includeAdult)
    override suspend fun searchTv(title: String, year: Int?, includeAdult: Boolean) = search(title, year, MetadataType.TV, includeAdult)
    private suspend fun search(title: String, year: Int?, type: MetadataType, includeAdult: Boolean): List<MetadataSearchResult>? = attempt { cfg ->
        if (!includeAdult) return@attempt emptyList()
        api.searchTrakt(title, year, type, cfg).mapNotNull { hit ->
            hit.tmdbId?.let { id -> MetadataSearchResult(id, type, hit.title, hit.originalTitle, hit.year, hit.overview, null, 0.0) }
        }
    }

    override suspend fun movieDetails(tmdbId: Int) = details(tmdbId, MetadataType.MOVIE)
    override suspend fun tvDetails(tmdbId: Int) = details(tmdbId, MetadataType.TV)
    private suspend fun details(id: Int, type: MetadataType): MovieDetails? = attempt { cfg ->
        val hit = api.lookupTrakt(id, type, cfg) ?: return@attempt null
        val language = settings.metadataConfig().resolvedLanguage
        val data = api.trakt(hit, cfg, language)
        val artwork = if (cfg.tvdbKey.isNotBlank()) {
            try {
                val tvdb = data.tvdbId ?: data.imdbId?.let { api.searchTvdb(data.title, null, type, cfg, it).singleOrNull()?.id }
                tvdb?.let { api.tvdb(it, cfg, language, type = type) }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        } else null
        val score = try { api.imdbRating(hit.id, type, cfg) }
            catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        val entity = if (type == MetadataType.TV) "shows" else "movies"
        val cast = try {
            val rows = api.traktObject("/$entity/${hit.id}/people", cfg).optJSONArray("cast")
            if (rows == null) emptyList() else (0 until rows.length()).mapNotNull { index ->
                rows.optJSONObject(index)?.optJSONObject("person")?.optString("name")?.takeIf { it.isNotBlank() }?.let { CastMember(it) }
            }.distinctBy { it.name }.take(20)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        val trailer = try {
            val rows = api.traktArray("/$entity/${hit.id}/videos", cfg)
            (0 until rows.length()).mapNotNull { index ->
                val item = rows.optJSONObject(index) ?: return@mapNotNull null
                val url = item.optString("url")
                Regex("https://(?:www\\.)?(?:youtube\\.com/watch\\?v=|youtu\\.be/)([A-Za-z0-9_-]{11})(?:[&?].*)?")
                    .matchEntire(url)?.groupValues?.get(1)
            }.firstOrNull()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        MovieDetails(tmdbId = id, title = data.title, year = data.year, overview = data.overview ?: artwork?.overview,
            posterPath = artwork?.artwork, backdropPath = null, rating = score, genres = data.genres,
            cast = cast, trailerKey = trailer, logoPath = artwork?.logo, tvdbId = data.tvdbId)
    }

    override suspend fun trendingPage(type: MetadataType, page: Int): TrendingFeedPage? = attempt { cfg ->
        require(type != MetadataType.EPISODE && page > 0)
        val kind = if (type == MetadataType.TV) "shows" else "movies"
        val itemKey = if (type == MetadataType.TV) "show" else "movie"
        val rows = api.traktArray("/$kind/trending?page=$page&limit=20&extended=full", cfg)
        val hits = (0 until rows.length()).mapNotNull { index ->
            val wrapper = rows.optJSONObject(index) ?: return@mapNotNull null
            val item = wrapper.optJSONObject(itemKey) ?: return@mapNotNull null
            val id = item.optJSONObject("ids")?.optInt("tmdb")?.takeIf { it > 0 } ?: return@mapNotNull null
            TrendingCandidate(id, type, item.getString("title"), null, item.optInt("year").takeIf { it > 0 },
                item.optString("overview").takeIf { it.isNotBlank() && it != "null" }, null, null, null,
                wrapper.optDouble("watchers", 0.0), (page - 1) * 20 + index + 1)
        }
        TrendingFeedPage(page, if (rows.length() == 20) page + 1 else page, hits)
    }

    override suspend fun tvSeasonBundle(tvId: Int, seasons: List<Int>): List<SeasonEpisode>? = attempt { cfg ->
        val hit = api.lookupTrakt(tvId, MetadataType.TV, cfg) ?: return@attempt null
        seasons.distinct().take(5).flatMap { season ->
            val rows = api.traktArray("/shows/${hit.id}/seasons/$season?extended=full", cfg)
            (0 until rows.length()).mapNotNull { index ->
                val item = rows.optJSONObject(index) ?: return@mapNotNull null
                val episode = item.optInt("number").takeIf { it > 0 } ?: return@mapNotNull null
                fun text(key: String) = item.optString(key).takeIf { it.isNotBlank() && it != "null" }
                SeasonEpisode(season, episode, EpisodeDetails(text("title"), text("overview"), null, text("first_aired")?.take(10), null))
            }
        }
    }
}
