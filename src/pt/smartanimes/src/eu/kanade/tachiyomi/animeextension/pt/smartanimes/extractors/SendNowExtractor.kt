package eu.kanade.tachiyomi.animeextension.pt.smartanimes.extractors

import android.util.Log
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class SendNowExtractor(private val client: OkHttpClient, private val headers: Headers) {

    val tag by lazy { javaClass.simpleName }

    fun videosFromUrl(url: String, name: String): List<Video> {
        Log.d(tag, "Fetching videos from: $url")

        val secChUa =
            "\"Google Chrome\";v=\"143\", \"Chromium\";v=\"143\", \"Not/A)Brand\";v=\"24\""

        val userAgent = headers["User-Agent"]
            ?: "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Mobile Safari/537.36"

        val newHeaders = headers.newBuilder().apply {
            removeAll("Referer")
            set("Accept-Encoding", "deflate")
            set("cache-control", "max-age=600")
            set("Connection", "keep-alive")
            set("Host", url.toHttpUrl().host)
            set("sec-ch-ua", secChUa)
            set("sec-ch-ua-mobile", "?1")
            set("sec-ch-ua-platform", "\"Android\"")
            set("Sec-Fetch-Dest", "document")
            set("Sec-Fetch-Mode", "navigate")
            set("Sec-Fetch-Site", "none")
            set("Sec-Fetch-User", "?1")
            set("Upgrade-Insecure-Requests", "1")
            set("User-Agent", userAgent)
        }.build()

        val document = client.newCall(GET(url, newHeaders)).execute().asJsoup()
        var videoUrl = ""

        if ("Download Challenge" == document.title()) {
            val noRedirectClient = client.newBuilder()
                .followRedirects(false)
                .build()

            val id = document.selectFirst("form > [name='id']")!!.attr("value")
            var formBody = FormBody.Builder()
                .add("op", "download2")
                .add("id", id)
                .build()

            val src = noRedirectClient.newCall(
                POST(
                    "https://${url.toHttpUrl().host}/",
                    headers = headers,
                    formBody,
                ),
            ).execute().headers["location"]?.takeIf { it.isNotEmpty() } ?: return emptyList()

            videoUrl = src.replaceAfterLast("/", "video.mp4")
        } else {
            val source = document.selectFirst("source") ?: return emptyList()
            videoUrl = source.attr("src")
        }
        Log.d(tag, "VIDEO URL: $videoUrl")

        val videoHeaders = Headers.headersOf("Referer", "https://${url.toHttpUrl().host}/")

        return listOf(
            Video(videoUrl, name, videoUrl, videoHeaders),
        )
    }
}
