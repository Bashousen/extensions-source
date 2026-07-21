package eu.kanade.tachiyomi.animeextension.pt.anikyuu

import android.util.Log
import eu.kanade.tachiyomi.animeextension.pt.anikyuu.extractors.EmTurboExtractor
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.filemoonextractor.FilemoonExtractor
import eu.kanade.tachiyomi.lib.hashvideoidextractor.HashVideoIdExtractor
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
    private val hashVideoIdExtractor by lazy { HashVideoIdExtractor(client) }

    override fun getVideoList(url: String, name: String): List<Video> {
        Log.d(tag, "Fetching videos from: $url")

        return when {
            "filemoon" in url -> filemoonExtractor.videosFromUrl(url, headers = headers, referer = baseUrl)
            "byselapuix" in url -> filemoonExtractor.videosFromUrl(url, headers = headers, referer = baseUrl)
            "turbo" in url -> emTurboExtractor.getVideos(url)
            "/#" in url -> hashVideoIdExtractor.videosFromUrl(url, headers)

            else -> emptyList()
        }
    }
}
