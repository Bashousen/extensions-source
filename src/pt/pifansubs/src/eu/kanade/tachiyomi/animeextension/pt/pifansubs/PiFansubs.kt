package eu.kanade.tachiyomi.animeextension.pt.pifansubs

import eu.kanade.tachiyomi.animeextension.pt.pifansubs.extractors.BlembedExtractor
import eu.kanade.tachiyomi.animeextension.pt.pifansubs.extractors.Incvideo1Extractor
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.abyssextractor.AbyssExtractor
import eu.kanade.tachiyomi.lib.dailymotionextractor.DailymotionExtractor
import eu.kanade.tachiyomi.lib.filemoonextractor.FilemoonExtractor
import eu.kanade.tachiyomi.lib.vidhideextractor.VidHideExtractor
import eu.kanade.tachiyomi.multisrc.dooplay.DooPlay
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import eu.kanade.tachiyomi.util.parallelFlatMapBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class PiFansubs : DooPlay(
    "pt-BR",
    "Pi Fansubs",
    "https://pifansubs.club",
) {

    override fun headersBuilder() = super.headersBuilder()
        .add("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")

    override val prefQualityValues = arrayOf("360p", "480p", "720p", "1080p")
    override val prefQualityEntries = prefQualityValues

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/episodios/page/$page", headers)

    // ============================== Popular ===============================
    override fun popularAnimeSelector(): String = "div#featured-titles div.poster"

    // =========================== Anime Details ============================
    override fun Document.getDescription(): String {
        return select("$additionalInfoSelector p")
            .eachText()
            .joinToString("\n\n") + "\n"
    }

    // ============================ Video Links =============================
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val players = document.select("div.source-box:not(#source-player-trailer) iframe")
        return players.parallelFlatMapBlocking(::getPlayerVideos)
    }

    private fun getPlayerUrl(player: Element): String {
        return player.attr("data-src").ifEmpty { player.attr("src") }.let {
            when {
                !it.startsWith("http") -> "https:" + it
                else -> it
            }
        }
    }

    private val blembedExtractor by lazy { BlembedExtractor(client, headers) }
    private val vidHideExtractor by lazy { VidHideExtractor(client, headers) }
    private val abyssExtractor by lazy { AbyssExtractor(client) }
    private val filemoonExtractor by lazy { FilemoonExtractor(client) }
    private val dailymotionExtractor by lazy { DailymotionExtractor(client, headers) }
    private val incvideo1Extractor by lazy { Incvideo1Extractor(client) }

    private fun getPlayerVideos(element: Element): List<Video> {
        val url = getPlayerUrl(element)

        return when {
            "jwplayer/?source" in url -> {
                val videoUrl = url.toHttpUrl().queryParameter("source") ?: return emptyList()
                listOf(Video(videoUrl, "PiFansubs", videoUrl))
            }
            "byse" in url || "cinebl" in url || "cinewaha9" in url -> filemoonExtractor.videosFromUrl(url)
            "filemoon" in url -> {
                val newUrl = url.toHttpUrl().newBuilder().host("filemoon.sx").build().toString()
                filemoonExtractor.videosFromUrl(newUrl)
            }
            "online/embed" in url -> incvideo1Extractor.videosFromUrl(url)
            "dailymotion" in url -> dailymotionExtractor.videosFromUrl(url)
            "tabvid" in url -> abyssExtractor.videosFromUrl(url, headers)
            "vidhide" in url -> vidHideExtractor.videosFromUrl(url)
            "luluvid" in url ->  vidHideExtractor.videosFromUrl(url, videoNameGen = { quality -> "Luluvid - $quality" })
            "blembed" in url -> blembedExtractor.videosFromUrl(url)
            else -> emptyList<Video>()
        }
    }
}
