package eu.kanade.tachiyomi.animeextension.pt.meusanimes

import android.util.Log
import eu.kanade.tachiyomi.animeextension.pt.meusanimes.extractors.MeusAnimesExtractor
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import eu.kanade.tachiyomi.util.parallelFlatMapBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.nodes.Document
import java.net.URLEncoder

class MeusAnimes : AnimeHttpSource() {

    override val name = "Meus Animes"
    override val baseUrl = "https://meusanimes.cc"
    override val lang = "pt-BR"
    override val supportsLatest = true

    override val client: OkHttpClient = OkHttpClient()

    // Requests: Popular anime request
    override fun popularAnimeRequest(page: Int): Request =
        GET(baseUrl, headers)

    // Search anime request
    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Request {
        val q = URLEncoder.encode(query, "UTF-8")
        return GET("$baseUrl/api/search?query=$q", headers)
    }

    // Parse Lists: Parse popular anime list
    override fun popularAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val animes = document.select(".top10_ani a")
            .map { element ->
                SAnime.create().apply {
                    title = element.select(".top10_ani_title").text()
                    url = element.attr("href")
                    thumbnail_url = element.selectFirst("img")
                        ?.attr("abs:src")
                        ?: ""
                }
            }

        return AnimesPage(animes, false)
    }

    // Latest updates use same parsing as popular
    override fun latestUpdatesParse(response: Response): AnimesPage {
        val doc = response.asJsoup()
        val episodes = doc.select("#listaEpisodesGrid a")

        val animes = episodes.map { element ->
            SAnime.create().apply {
                val epNum = element.selectFirst(".epNum")?.text() ?: ""
                val epName = element.selectFirst(".epNome")?.text() ?: ""

                title = "$epName - $epNum"
                url = element.attr("href")
                thumbnail_url = element.selectFirst("img")
                    ?.attr("abs:src")

                initialized = false
            }
        }

        val hasNextPage = doc.selectFirst("#paginationWrap a:last-child")?.hasAttr("title") ?: false

        return AnimesPage(animes, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/lista-de-episodios?page=$page", headers)

    // Parse search results from API
    override fun searchAnimeParse(response: Response): AnimesPage {
        val body = response.body.string()
        val json = JSONObject(body)

        val data = json.optJSONArray("data") ?: return AnimesPage(emptyList(), false)

        val animes = (0 until data.length()).map { i ->
            val obj = data.getJSONObject(i)

            SAnime.create().apply {
                title = obj.optString("name")
                url = obj.optString("url")

                thumbnail_url = obj.optString("poster")
                    .takeIf { it.isNotEmpty() } ?: ""

                initialized = true
            }
        }

        return AnimesPage(animes, false)
    }

    // No filters implemented
    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    // Parse core anime data from JSON
    private fun parseAnimeCore(json: JSONObject): CoreAnimeData {
        val title = json.optString("name")

        val altTitle = json.optString("nameOriginal")
            .takeIf { it.isNotBlank() }

        val description = json.optString("sinopse")
            .trim()
            .ifBlank { "Sinopse não disponível." }

        val status = when {
            json.optString("diaLancamento").isNotBlank() ->
                SAnime.ONGOING
            json.optInt("episodios") > 0 ->
                SAnime.COMPLETED
            else ->
                SAnime.UNKNOWN
        }

        return CoreAnimeData(
            title = title,
            altTitle = altTitle,
            description = description,
            status = status,
        )
    }

    // Fallback: parse anime details from meta tags
    private fun parseAnimeFromMeta(document: Document): SAnime = SAnime.create().apply {
        title = document.select("meta[property=og:title]").attr("content")
        description = document.select("meta[name=description]").attr("content")
        thumbnail_url = document.select("meta[property=og:image]").attr("content")
        initialized = true
    }

    // Data class for core anime information
    private data class CoreAnimeData(
        val title: String,
        val altTitle: String?,
        val description: String,
        val status: Int,
    )

    // Main anime details parser
    override fun animeDetailsParse(response: Response): SAnime {
        val document = getRealAnimeDoc(response.asJsoup())

        return SAnime.create().apply {
            title = document.selectFirst(".anime_titulo")?.text() ?: ""
            description = document.selectFirst("#sinopse_content")?.text() ?: ""
            thumbnail_url = document.selectFirst(".anime_thumb_left > img")?.attr("src") ?: ""

            val statusString = document.selectFirst(".anime_status > span:last-child")?.text() ?: ""
            status = when (statusString.lowercase()) {
                "completo" -> SAnime.COMPLETED
                "em lançamento" -> SAnime.ONGOING
                else -> SAnime.UNKNOWN
            }

            initialized = true
        }
    }

    // Alternative JSON parser (not used in current implementation)
    private fun parseAnimeFromJson(
        json: JSONObject,
        document: Document,
    ): SAnime = SAnime.create().apply {
        title = json.optString("name")

        // Studio
        author = json.optJSONObject("Studio")
            ?.optString("name")

        // Original title goes to "artist" field (Tachiyomi standard)
        artist = json.optString("nameOriginal").takeIf { it.isNotBlank() }

        val year = json.optInt("ano").takeIf { it > 0 }
        val synopsis = json.optString("sinopse")

        description = buildString {
            if (year != null) append("Ano: $year\n\n")
            append(synopsis)
        }

        genre = json.optJSONArray("Animegenero")
            ?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.getJSONObject(i)
                        .optJSONObject("Genero")
                        ?.optString("name")
                }.joinToString(", ")
            }

        status = when (json.optString("status").lowercase()) {
            "ended", "finalizado", "completo" -> SAnime.COMPLETED
            "releasing", "em lançamento", "andamento" -> SAnime.ONGOING
            else -> SAnime.UNKNOWN
        }

        thumbnail_url = json.optString("poster")
            .takeIf { it.isNotBlank() }
            ?.let { "https://image.tmdb.org/t/p/w500$it" }
            ?: document.select("meta[property=og:image]").attr("content")

        initialized = true
    }

    // Episode Details: Extract anime data from script tag in page
    private fun extractEpisodeData(url: String): List<Episode>? {
        val json by lazy { Json { ignoreUnknownKeys = true } }

        val newHeaders = headers.newBuilder()
            .set("x-requested-with", "XMLHttpRequest")
            .set("Referer", baseUrl)
            .build()

        return runCatching {
            val jsonString = client.newCall(GET("$url/data", newHeaders)).execute().body.string()
            val anime = json.decodeFromString<Anime>(jsonString)
            anime.data.episodes
        }.getOrNull()
    }

    // Episodes: Parse episode list from JSON data
    override fun episodeListParse(response: Response): List<SEpisode> {
        val animeUrl = getRealAnimeUrl(response) ?: return emptyList()
        val episodes = extractEpisodeData(animeUrl) ?: return emptyList()

        return episodes
            .map { episode ->
                SEpisode.create().apply {
                    name = "Temporada ${episode.season} x ${episode.number}"
                    episode_number = episode.number.toFloat()
                    url = episode.url
                }
            }.reversed()
    }

    // Videos: Video list request
    override fun videoListRequest(episode: SEpisode): Request {
        return GET(baseUrl + episode.url, headers)
    }

    // Parse video list from episode page
    override fun videoListParse(response: Response): List<Video> {
        val meusAnimesExtractor by lazy { MeusAnimesExtractor(client, headers) }
        val m3u8Integration by lazy { M3u8Integration(client) }

        val doc = response.asJsoup()
        val players = doc.select(".abaPlayer")

        if (players.isEmpty()) {
            val url = doc.selectFirst("iframe")?.attr("src") ?: return emptyList()
            val videos = meusAnimesExtractor.getVideosFromUrl(url, "legendado")

            return m3u8Integration.processVideoList(videos)
        }

        val videos = players.parallelFlatMapBlocking { player ->
            val url = player.attr("data-url")
            val prefix = player.text().lowercase()

            when {
                "meusanimes" in url -> meusAnimesExtractor.getVideosFromUrl(url, prefix)
                else -> emptyList()
            }
        }

        return m3u8Integration.processVideoList(videos)
    }

    // ============================= Utilities ==============================

    private val animeMenuSelector = ".episodio_controles_players a:has(#lista_ep)"

    private fun getRealAnimeUrl(response: Response): String? {
        val url = response.request.url.toString()

        return when {
            "/anime/" in url -> url
            else -> response.asJsoup()
                .selectFirst(animeMenuSelector)
                ?.attr("abs:href")
        }
    }

    /**
     * If the document comes from a episode page, this function will get the
     * real/expected document from the anime details page. else, it will return the
     * original document.
     *
     * @return A document from a anime details page.
     */
    private fun getRealAnimeDoc(document: Document): Document {
        val url = document.location()
        Log.d("getRealAnimeDoc", url)

        return if ("/anime" !in url) {
            val animeUrl = document.selectFirst(animeMenuSelector)!!.attr("abs:href")
            val req = client.newCall(GET(animeUrl, headers)).execute()
            req.asJsoup()
        } else {
            document
        }
    }
}
