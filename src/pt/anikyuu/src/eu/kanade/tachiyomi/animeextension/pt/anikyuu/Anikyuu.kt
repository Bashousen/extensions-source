package eu.kanade.tachiyomi.animeextension.pt.anikyuu

import android.util.Log
import eu.kanade.tachiyomi.animeextension.pt.anikyuu.extractors.EmTurboExtractor
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.filemoonextractor.FilemoonExtractor
import eu.kanade.tachiyomi.multisrc.animestream.AnimeStream

class Anikyuu : AnimeStream(
    "pt-BR",
    "Anikyuu",
    "https://anikyuu.to",
) {
    private val tag by lazy { javaClass.simpleName }

    override fun headersBuilder() = super.headersBuilder().add("Referer", baseUrl)

    // ============================ Video Links =============================
    override val prefQualityValues = arrayOf("1080p", "720p", "480p", "360p", "240p")
    override val prefQualityEntries = prefQualityValues

    // ============================ Video Links =============================

    private val filemoonExtractor by lazy { FilemoonExtractor(client) }
    private val emTurboExtractor by lazy { EmTurboExtractor(client, headers) }

    override fun getVideoList(url: String, name: String): List<Video> {
        Log.d(tag, "Fetching videos from: $url")

        return when {
            listOf(
                "filemoon",
                "byselapuix",
            ).any(url::contains) -> {
                val url = if (url.count { it == '/' } > 4) url.substringBeforeLast("/") else url
                filemoonExtractor.videosFromUrl(url, headers = headers, referer = baseUrl)
            }
            "turbovidhls.com" in url -> emTurboExtractor.getVideos(url)

            else -> emptyList()
        }
    }
}
