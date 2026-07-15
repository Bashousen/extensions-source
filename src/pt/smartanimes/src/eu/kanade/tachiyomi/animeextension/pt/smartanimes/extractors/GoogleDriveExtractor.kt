package eu.kanade.tachiyomi.animeextension.pt.smartanimes.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class GoogleDriveExtractor(private val client: OkHttpClient, private val headers: Headers) {

    fun videosFromUrl(originalUrl: String, videoName: String = "Video"): List<Video> {
        val itemId = originalUrl.split("/")[5]
        val url = "https://drive.usercontent.google.com/download?id=$itemId"
        val docHeaders = headers.newBuilder().apply {
            add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            removeAll("Referer")
        }.build()

        val document = client.newCall(
            GET(url, docHeaders),
        ).execute().asJsoup()

        val videoUrl = url.toHttpUrl().newBuilder().apply {
            val querys = document.select("input[type=hidden]").ifEmpty { return emptyList() }
            querys.forEach {
                setQueryParameter(it.attr("name"), it.attr("value"))
            }
        }.build().toString()

        return listOf(
            Video(videoUrl, videoName, videoUrl, docHeaders),
        )
    }
}
