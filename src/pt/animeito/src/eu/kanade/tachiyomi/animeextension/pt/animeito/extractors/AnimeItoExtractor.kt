package eu.kanade.tachiyomi.animeextension.pt.animeito.extractors

import android.util.Base64
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.m3u8server.M3u8Integration
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.OkHttpClient

class AnimeItoExtractor(private val client: OkHttpClient, private val headers: Headers) {
    private val playlistUtils by lazy { PlaylistUtils(client, headers) }
    private val m3u8Integration by lazy { M3u8Integration(client) }

    fun videosFromUrl(url: String): List<Video> {
        val playerDoc = client.newCall(GET(url, headers)).execute().asJsoup()
        val encodedScript = playerDoc.selectFirst("[type=\"text/javascript\"]:nth-child(3)")
            ?.data() ?: return emptyList()

        val arguments = REGEX_ARGUMENTS.find(encodedScript)?.groupValues ?: return emptyList()

        val dictionary: List<String> = Json.decodeFromString(arguments[1])
        val indices: List<Int> = Json.decodeFromString(arguments[2])
        val xorKey: String = arguments[3]

        val decodedScript = decodeScript(dictionary, indices, xorKey)

        return if ("googlevideo" in decodedScript) {
            decodedScript.substringAfter("sources\":")
                .substringBefore("]")
                .split("{")
                .drop(1)
                .map {
                    val videoUrl = it.substringAfter("file\":\"").substringBefore('"')
                    val quality = it.substringAfter("label\":\"").substringBefore('"')
                    Video(videoUrl, "Animei.to - $quality", videoUrl, headers)
                }
        } else {
            val masterPlaylistUrl = decodedScript.substringAfter("sources\":")
                .substringAfter("file\":\"")
                .substringBefore('"')

            val videos = playlistUtils.extractFromHls(masterPlaylistUrl, videoNameGen = { "Animei.to - $it" })

            m3u8Integration.processVideoList(videos)
        }
    }

    private fun decodeScript(dictionary: List<String>, indices: List<Int>, xorKeyBase64: String): String {
        val base64String = indices.joinToString("") { i -> dictionary[i] }
        val encodedPayload = Base64.decode(base64String, Base64.DEFAULT)
        val xorKey = Base64.decode(xorKeyBase64, Base64.DEFAULT)

        val decodedBytes = ByteArray(encodedPayload.size)
        for (i in encodedPayload.indices) {
            decodedBytes[i] = (
                encodedPayload[i].toInt() xor
                    xorKey[i % xorKey.size].toInt()
                ).toByte()
        }

        return String(decodedBytes, Charsets.UTF_8)
    }

    companion object {
        val REGEX_ARGUMENTS = """\((\[[^]]*]),(\[[^]]*]),"([^"]*)""".toRegex()
    }
}
