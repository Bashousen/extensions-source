package eu.kanade.tachiyomi.animeextension.pt.animeplay.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient

class BurstcloudExtractor(private val client: OkHttpClient) {

    fun videosFromUrl(url: String, headers: Headers): List<Video> {
        val document = client.newCall(GET(url, headers)).execute().asJsoup()
        val fileId = document.selectFirst("#player")?.attr("data-file-id") ?: return emptyList()

        val newHeaders = headers.newBuilder()
            .set("referer", url)
            .build()

        val body = FormBody.Builder()
            .add("fileId", fileId)
            .build()

        val videoUrl = client.newCall(POST("https://www.burstcloud.co/file/play-request/", newHeaders, body))
            .execute().body.string()
            .substringAfter("\"cdnUrl\":\"")
            .substringBefore("\"")

        return listOf(Video(videoUrl, "720p", videoUrl, newHeaders))
    }
}
