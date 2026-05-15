package eu.kanade.tachiyomi.animeextension.pt.anikyuu.extractors

import android.util.Log
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.m3u8server.M3u8ServerManager
import okhttp3.OkHttpClient
import java.net.URLEncoder

/**
 * M3U8 Server integration with Q1N extension
 */
class M3u8Integration(
    private val client: OkHttpClient,
    private val serverManager: M3u8ServerManager = M3u8ServerManager(),
) {

    private val tag by lazy { javaClass.simpleName }
    private var isInitialized = false

    private fun initializeServer() {
        if (!isInitialized && !serverManager.isRunning()) {
            try {
                serverManager.startServer() // Uses random port by default
                isInitialized = true
                Log.d(tag, "M3U8 server initialized on port: ${serverManager.getServerUrl()}")
            } catch (e: Exception) {
                // Log error but don't crash
                Log.e(tag, "Failed to start M3U8 server: ${e.message}")
            }
        }
    }

    /**
     * Processes an M3U8 video through the local server
     * @param originalVideo Original video with M3U8 URL
     * @return Processed video with local URL
     */
    private fun processM3u8Video(originalVideo: Video): Video {
        val serverUrl = serverManager.getServerUrl() ?: return originalVideo

        val encodedUrl = URLEncoder.encode(originalVideo.url, "UTF-8")
        val proxiedUrl = "$serverUrl/m3u8/playlist.m3u8?url=$encodedUrl"

        return Video(
            url = proxiedUrl,
            quality = originalVideo.quality,
            videoUrl = proxiedUrl,
            subtitleTracks = originalVideo.subtitleTracks,
            audioTracks = originalVideo.audioTracks,
            headers = originalVideo.headers,
        )
    }

    /**
     * Processes a list of videos, identifying and processing only M3U8 files
     * @param videos Original video list
     * @return Processed video list
     */
    fun processVideoList(videos: List<Video>): List<Video> {
        initializeServer()
        return videos.map { video ->
            processM3u8Video(video)
        }
    }
}
