package eu.kanade.tachiyomi.animeextension.pt.donghuanosekai.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.bloggerextractor.BloggerExtractor
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

class DonghuaNoSekaiExtractor(
    private val client: OkHttpClient,
    private val headers: Headers,
) {
    fun videosFromDocument(document: Document): List<Video> {
        val bloggerExtractor by lazy { BloggerExtractor(client) }
        val dailymotionExtractor by lazy { DailymotionExtractor(client, headers) }

        val iframe = document.selectFirst("iframe")
        val playerId = document.location().toHttpUrl()
            .queryParameter("type")
            ?.toIntOrNull()?.plus(1) ?: 1
        val playerName = "Player $playerId"

        if (iframe == null) {
            val source = document.selectFirst("video > source") ?: return emptyList()
            val quality = source.attr("size") + "p"
            val url = source.attr("src")

            return listOf(Video(url, "$playerName - $quality", url, headers))
        }

        val iframeUrl = iframe.attr("src")

        return when {
            "m3u8" in iframeUrl -> {
                val url = iframeUrl.toHttpUrl().run {
                    queryParameter("v") ?: queryParameter("id") ?: queryParameter("url")?.trim()
                } ?: return emptyList()

                val quality = url.substringAfter("_").substringBefore("_")
                listOf(Video(url, "$playerName - $quality", url, headers))
            }
            "dailymotion" in iframeUrl -> dailymotionExtractor.videosFromUrl(iframeUrl)
            "blogger" in iframeUrl -> bloggerExtractor.videosFromUrl(iframeUrl, headers)
            "blogspot" in iframeUrl -> {
                bloggerExtractor.videosFromUrl("https://www.blogger.com/video.g?token=$iframeUrl", headers)
            }

            else -> emptyList()
        }
    }
}
