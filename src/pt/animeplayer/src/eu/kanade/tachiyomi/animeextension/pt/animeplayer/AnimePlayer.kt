package eu.kanade.tachiyomi.animeextension.pt.animeplayer

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.bloggerextractor.BloggerExtractor
import eu.kanade.tachiyomi.multisrc.dooplay.DooPlay
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import eu.kanade.tachiyomi.util.parallelFlatMapBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class AnimePlayer : DooPlay(
    "pt-BR",
    "AnimePlayer",
    "https://animeplayer.com.br",
) {

    // ============================== Popular ===============================
    override fun popularAnimeSelector() = "div#archive-content article div.poster"

    override fun popularAnimeRequest(page: Int) = GET("$baseUrl/animes/")

    override fun popularAnimeNextPageSelector() = "a > i#nextpagination"

    // =============================== Latest ===============================
    override val latestUpdatesPath = "episodios"

    override fun latestUpdatesNextPageSelector() = popularAnimeNextPageSelector()

    // =========================== Anime Details ============================
    override fun animeDetailsParse(document: Document) = SAnime.create().apply {
        val doc = getRealAnimeDoc(document)
        val content = doc.selectFirst("div#contenedor > div.data")!!
        doc.selectFirst("div.sheader div.poster > img")!!.let {
            thumbnail_url = it.getImageUrl()
            title = it.attr("alt").ifEmpty {
                content.selectFirst("div.data > h1")!!.text()
            }
        }

        genre = content.select("div.sgeneros > a")
            .eachText()
            .joinToString()
    }

    // ============================== Episodes ==============================
    override fun episodeListParse(response: Response) = super.episodeListParse(response).reversed()

    override fun getSeasonEpisodes(season: Element): List<SEpisode> {
        val seasonName = season.selectFirst("span.title")!!.text()
        return season.select(episodeListSelector()).mapNotNull { element ->
            runCatching {
                episodeFromElement(element, seasonName)
            }.onFailure { it.printStackTrace() }.getOrNull()
        }
    }

    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException()

    override fun episodeFromElement(element: Element, seasonName: String) = SEpisode.create().apply {
        val epNum = element.selectFirst("div.episodiotitle p")!!.text()
            .trim()
            .let(episodeNumberRegex::find)
            ?.groupValues
            ?.last() ?: "0"
        val href = element.selectFirst("a[href]")!!
        episode_number = epNum.toFloatOrNull() ?: 0F
        name = "$seasonName x Episódio $epNum"
        setUrlWithoutDomain(href.absUrl("href"))
    }

    // ============================ Video Links =============================
    override val prefQualityValues = arrayOf("360p", "720p")
    override val prefQualityEntries = prefQualityValues

    private val bloggerExtractor by lazy { BloggerExtractor(client) }

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val urls = document.select(".player-placeholder").eachAttr("data-src")
            .map { it.toHttpUrl().queryParameter("link") ?: it }.distinct()

        val quality = document
            .selectFirst("span.qualityx")
            ?.text()
            ?.substringAfterLast(" ")
            ?: "Default"

        return urls.parallelFlatMapBlocking { url ->
            when {
                ".mp4" in url -> {
                    listOf(
                        Video(url, quality, url, headers),
                    )
                }

                "api/index.php?token" in url -> {
                    val sourcesRegex = Regex("sources: (.*?]),")
                    val urlsRegex = Regex("""file"?:"(.*?)"""")

                    val document = client.newCall(GET(url, headers)).execute().asJsoup()
                    val script = document.selectFirst("script:containsData(sources)")!!.data()

                    val sources = sourcesRegex.find(script)!!.groupValues[1]
                    val url = urlsRegex.find(sources)!!.groupValues[1]
                        .let {
                            "https://$it".toHttpUrl().queryParameter("src")!!
                        }

                    bloggerExtractor.videosFromUrl(url, headers)
                }

                else -> emptyList()
            }
        }
    }

    // ============================== Filters ===============================
    override fun genresListSelector() = "ul.genres a"

    // ============================= Utilities ==============================
    override val animeMenuSelector = "div.pag_episodes div.item a[href] i.icon-bars"
}
