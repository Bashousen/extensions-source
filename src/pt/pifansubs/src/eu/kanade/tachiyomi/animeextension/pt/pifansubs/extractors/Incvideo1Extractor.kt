package eu.kanade.tachiyomi.animeextension.pt.pifansubs.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.OkHttpClient

class Incvideo1Extractor(private val client: OkHttpClient) {
    fun videosFromUrl(url: String): List<Video> {
        return client.newCall(GET(url)).execute()
            .body.string()
            .substringAfter("Playerjs({")
            .substringAfter("file:\"")
            .substringBefore("\"")
            .split(",")
            .map {
                val videoUrl = it.substringAfter("]")
                val quality = it
                    .substringAfter("[", "")
                    .substringBefore("]")
                    .ifEmpty { videoUrl.substringAfterLast("_").substringBefore(".") }
                val headers = Headers.headersOf("Referer", videoUrl)
                Video(videoUrl, "Incvideo1 - $quality", videoUrl, headers = headers)
            }
    }
}
