package eu.kanade.tachiyomi.animeextension.pt.animesroll

import android.util.Log
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.vidmolyextractor.VidMolyExtractor
import eu.kanade.tachiyomi.lib.voeextractor.VoeExtractor
import eu.kanade.tachiyomi.multisrc.dooplay.DooPlay
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.asJsoup
import eu.kanade.tachiyomi.util.parallelCatchingFlatMapBlocking
import okhttp3.FormBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class AnimesROLL : DooPlay(
    "pt-BR",
    "Animes ROLL",
    "https://anroll.plus",
) {

    private val tag by lazy { javaClass.simpleName }

    override val versionId: Int = 2

    // ============================== Popular ===============================
    override fun popularAnimeSelector() = "article div.poster"
    override fun popularAnimeRequest(page: Int) = GET("$baseUrl/anime/page/$page", headers)
    override fun popularAnimeNextPageSelector() = ".pagination .current + a"

    // =============================== Latest ===============================
    override val latestUpdatesPath = "episodio"
    override fun latestUpdatesNextPageSelector() = popularAnimeNextPageSelector()

    // =============================== Search ===============================
    override fun searchAnimeSelector() = "div.result-item article div.thumbnail > a"
    override fun searchAnimeFromElement(element: Element) = popularAnimeFromElement(element)

    // =========================== Anime Details ============================
    override val additionalInfoSelector = "div.wp-content"

    override fun Document.getDescription(): String {
        return select("$additionalInfoSelector p")
            .eachText()
            .joinToString("\n")
    }

    override fun animeDetailsParse(document: Document): SAnime {
        val doc = getRealAnimeDoc(document)
        val sheader = doc.selectFirst("div.sheader")!!
        return SAnime.create().apply {
            setUrlWithoutDomain(doc.location())
            sheader.selectFirst("div.poster > img")!!.let {
                thumbnail_url = it.getImageUrl()
                title = it.attr("alt").ifEmpty {
                    sheader.selectFirst("div.data > h1")!!.text()
                }
            }

            genre = sheader.select("div.data div.sgeneros > a")
                .eachText()
                .joinToString()

            doc.selectFirst("div#info")?.let { info ->
                description = buildString {
                    append(doc.getDescription())
                    additionalInfoItems.forEach {
                        info.getInfo(it)?.let(::append)
                    }
                }
            }
        }
    }

    // ============================== Episodes ==============================
    override fun getSeasonEpisodes(season: Element): List<SEpisode> {
        val seasonName = season.selectFirst("span.se-t")?.text()
        val episodeElements = season.select(episodeListSelector()).ifEmpty { getEpisodeElements(season) }

        return episodeElements.mapNotNull { element ->
            runCatching {
                if (seasonName.isNullOrBlank()) {
                    episodeFromElement(element)
                } else {
                    episodeFromElement(element, seasonName)
                }
            }.onFailure { it.printStackTrace() }.getOrNull()
        }
    }

    private fun getEpisodeElements(season: Element): List<Element> {
        val body = FormBody.Builder()
            .add("action", "sac_load_season_episodes")
            .add("season_id", season.attr("data-season-id"))
            .add("tmdb", season.attr("data-tmdb"))
            .build()

        return client.newCall(POST("$baseUrl/wp-admin/admin-ajax.php", headers, body))
            .execute().body.string()
            .substringAfter("\"html\":\"")
            .replace("\\", "")
            .let {
                val doc = Jsoup.parse(it)
                doc.select("li")
            }
    }

    // ============================ Video Links =============================
    override val prefQualityValues = arrayOf("360p", "480p", "720p", "1080p")
    override val prefQualityEntries = prefQualityValues

    private val vidmolyExtractor by lazy { VidMolyExtractor(client, headers) }
    private val voeExtractor by lazy { VoeExtractor(client, headers) }

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val players = document.select("#playex .sa-play-cover")

        return players.parallelCatchingFlatMapBlocking { player ->
            val url = player.attr("data-src")
            Log.d(tag, "Fetching videos from: $url")

            when {
                "vidmoly" in url -> vidmolyExtractor.videosFromUrl(url)
                "voe" in url -> voeExtractor.videosFromUrl(url)

                else -> emptyList()
            }
        }
    }

    // ============================== Filters ===============================
    override fun genresListRequest() = popularAnimeRequest(0)
    override fun genresListSelector() = "div.filter > div.select:first-child option:not([disabled])"

    override fun genresListParse(document: Document): Array<Pair<String, String>> {
        val items = document.select(genresListSelector()).map {
            val name = it.text()
            val value = it.attr("value").substringAfter("$baseUrl/")
            Pair(name, value)
        }.toTypedArray()

        return if (items.isEmpty()) {
            items
        } else {
            arrayOf(Pair(selectFilterText, "")) + items
        }
    }

    // ============================= Utilities ==============================
    override val animeMenuSelector = "div.pag_episodes div.item a[href] i.icon-bars"

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString(videoSortPrefKey, videoSortPrefDefault)!!
        return sortedWith(
            compareBy(
                { it.quality.contains(quality) },
                { REGEX_QUALITY.find(it.quality)?.groupValues?.get(1)?.toIntOrNull() ?: 0 },
            ),
        ).reversed()
    }

    companion object {
        private val REGEX_QUALITY by lazy { Regex("""(\d+)p""") }
    }
}
