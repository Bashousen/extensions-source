package eu.kanade.tachiyomi.animeextension.pt.betteranimeio

import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.bloggerextractor.BloggerExtractor
import eu.kanade.tachiyomi.multisrc.dooplay.DooPlay
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.asJsoup
import eu.kanade.tachiyomi.util.parallelCatchingFlatMapBlocking
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class BetterAnimeIo : DooPlay(
    "pt-BR",
    "BetterAnimeIo",
    "https://betteranime.io",
) {
    private val contentUrl = "$baseUrl/animes"

    // ============================== Popular ===============================
    override fun popularAnimeSelector() = "div#featured-titles article.item div.poster"

    override fun popularAnimeRequest(page: Int): Request = GET(contentUrl, headers)

    // =============================== Latest ===============================
    override fun latestUpdatesSelector() = "div#archive-content article.item div.poster"

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$contentUrl/page/$page", headers)

    // ============================== Episodes ==============================
    override fun getSeasonEpisodes(season: Element): List<SEpisode> {
        val seasonName = season.selectFirst("span.se-t")?.text() ?: "1"
        return season.select(episodeListSelector()).mapNotNull { element ->
            runCatching {
                episodeFromElement(element, seasonName)
            }.onFailure { it.printStackTrace() }.getOrNull()
        }
    }

    override fun episodeFromElement(element: Element, seasonName: String): SEpisode {
        return SEpisode.create().apply {
            val link = element.selectFirst(".episodiotitle a")!!
            val episodeText = link.text()
            val epNum = episodeText.substringBefore(" -").trim()

            episode_number = epNum.toFloatOrNull() ?: 0F
            name = "$episodeSeasonPrefix $seasonName x $epNum"
            setUrlWithoutDomain(link.attr("href"))

            element.selectFirst(".timeAgo[data-time]")?.attr("data-time")?.let { dateStr ->
                date_upload = parseIsoDate(dateStr)
            }
        }
    }

    private fun parseIsoDate(dateStr: String): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                .parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    // ============================== Filters ===============================
    override fun genresListRequest(): Request = GET("$baseUrl/episodios/", headers)

    override fun genresListSelector() = "nav.genres ul.genres li a"

    // ============================ Video Links =============================
    private val bloggerExtractor by lazy { BloggerExtractor(client) }

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val players = document.select("ul#playeroptionsul li")
        return players.parallelCatchingFlatMapBlocking(::getPlayerVideos)
    }

    private fun getPlayerVideos(player: Element): List<Video> {
        val url = getPlayerUrl(player) ?: return emptyList()
        if (url.isEmpty()) return emptyList()

        return when {
            "jwplayer?source=" in url || "jwplayer/?source=" in url -> {
                val videoUrl = url.toHttpUrl().queryParameter("source") ?: return emptyList()

                bloggerExtractor.videosFromUrl(videoUrl, headers)
            }
            else -> emptyList()
        }
    }

    private fun getPlayerUrl(player: Element): String? {
        val body = FormBody.Builder()
            .add("action", "doo_player_ajax")
            .add("post", player.attr("data-post"))
            .add("nume", player.attr("data-nume"))
            .add("type", player.attr("data-type"))
            .build()

        return client.newCall(POST("$baseUrl/wp-admin/admin-ajax.php", headers, body))
            .execute().body.string()
            .substringAfter("\"embed_url\":\"")
            .substringBefore("\",")
            .replace("\\", "")
            .takeIf(String::isNotBlank)
    }

    // ============================= Utilities ==============================
    override val prefQualityValues = arrayOf("360p", "720p", "1080p")
    override val prefQualityEntries = prefQualityValues
}
