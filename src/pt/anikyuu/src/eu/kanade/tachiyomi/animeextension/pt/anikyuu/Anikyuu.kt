package eu.kanade.tachiyomi.animeextension.pt.anikyuu

import android.util.Log
import eu.kanade.tachiyomi.animeextension.pt.anikyuu.extractors.EmTurboExtractor
import eu.kanade.tachiyomi.animeextension.pt.anikyuu.extractors.MoonExtractor
import eu.kanade.tachiyomi.animesource.model.Video
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

    private val moonExtractor by lazy { MoonExtractor(client, headers, baseUrl) }
    private val emTurboExtractor by lazy { EmTurboExtractor(client, headers) }

    override fun getVideoList(url: String, name: String): List<Video> {
        Log.d(tag, "Fetching videos from: $url")

        return when {
            listOf(
                "filemoon",
                "byselapuix",
            ).any(url::contains) -> {
                val url = if (url.count { it == '/' } > 4) url.substringBeforeLast("/") else url
                moonExtractor.videosFromUrl(url, "$name - ")
            }
            "turbovidhls.com" in url -> emTurboExtractor.getVideos(url)

            else -> emptyList()
        }
    }
}
