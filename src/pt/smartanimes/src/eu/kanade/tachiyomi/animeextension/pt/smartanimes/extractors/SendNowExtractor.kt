package eu.kanade.tachiyomi.animeextension.pt.smartanimes.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

class SendNowExtractor(private val client: OkHttpClient, private val headers: Headers) {

    fun videosFromUrl(url: String, name: String): List<Video> {
        val document = client.newCall(GET(url, headers)).execute().asJsoup()
        val videoUrl = document.selectFirst("source")?.attr("src")
            ?: bypassCloudfare(document) ?: return emptyList()

        val videoHeaders = Headers.headersOf("Referer", "https://${url.toHttpUrl().host}/")

        return listOf(
            Video(videoUrl, name, videoUrl, videoHeaders),
        )
    }

    private fun bypassCloudfare(doc: Document): String? {
        val noRedirectClient = client.newBuilder()
            .followRedirects(false)
            .build()

        val id = doc.select("form > [name='id']").attr("value") ?: return null
        var formBody = FormBody.Builder()
            .add("op", "download2")
            .add("id", id)
            .build()

        val videoUrl = noRedirectClient.newCall(
            POST(
                "https://${doc.location().toHttpUrl().host}/",
                headers = headers,
                formBody,
            ),
        ).execute().header("location") ?: return null

        // the last segment has to be renamed to "video.mp4" in order to play.
        return videoUrl.replaceAfterLast("/", "video.mp4")
    }
}
