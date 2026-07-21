package eu.kanade.tachiyomi.animeextension.pt.anikyuu.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.m3u8server.M3u8Integration
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

class EmTurboExtractor(private val client: OkHttpClient, private val headers: Headers) {

    private val playlistExtractor by lazy { PlaylistUtils(client, headers) }

    private val m3u8Integration by lazy { M3u8Integration(client) }

    fun getVideos(url: String): List<Video> {
        val document = client.newCall(GET(url, headers)).execute().asJsoup()

        val urlPlay = document.selectFirst("#video_player")
            ?.attr("data-hash")
            ?: return emptyList()

        if (urlPlay.toHttpUrlOrNull() == null) return emptyList()

        val videos = playlistExtractor.extractFromHls(urlPlay, url, videoNameGen = { quality -> "EmTurboVid - $quality" })
            .distinctBy { it.url } // they have the same stream repeated twice in the playlist file

        return runBlocking { m3u8Integration.processVideoList(videos) }
    }
}
