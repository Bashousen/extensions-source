package eu.kanade.tachiyomi.animeextension.pt.animeito.extractors

import android.util.Base64
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.OkHttpClient

class AnimeItoExtractor(private val client: OkHttpClient, private val headers: Headers) {
    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    fun videosFromUrl(url: String): List<Video> {
        val playerDoc = client.newCall(GET(url, headers)).execute().asJsoup()
        val encodedScript = playerDoc.selectFirst("[type=\"text/javascript\"]:nth-child(3)")
            ?.data() ?: return emptyList()

        val arguments = REGEX_ARGUMENTS.find(encodedScript)?.groupValues ?: return emptyList()

        val charDictionary: List<String> = Json.decodeFromString(arguments[1])
        val charIndices: List<Int> = Json.decodeFromString(arguments[2])
        val xorKeyBase64: String = arguments[3]

        val decodedScript = decodeScript(charDictionary, charIndices, xorKeyBase64)

        return if ("googlevideo" in decodedScript) {
            decodedScript.substringAfter("sources\":").substringBefore("]")
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

            playlistUtils.extractFromHls(masterPlaylistUrl, videoNameGen = { "Animei.to - $it" })
        }
    }

    private fun decodeScript(charDictionary: List<String>, charIndices: List<Int>, xorKeyBase64: String): String {
        val base64String = charIndices.joinToString("") { i -> charDictionary[i] }
        val encodedPayload = String(Base64.decode(base64String, Base64.DEFAULT), Charsets.ISO_8859_1)
        val xorKey = String(Base64.decode(xorKeyBase64, Base64.DEFAULT), Charsets.ISO_8859_1)

        val decodedBytes = ByteArray(encodedPayload.length)
        for (i in encodedPayload.indices) {
            decodedBytes[i] = (
                encodedPayload[i].code xor
                    xorKey[i % xorKey.length].code
                ).toByte()
        }
        val decodedScript = String(decodedBytes, Charsets.UTF_8)

        return decodedScript
    }

    companion object {
        val REGEX_ARGUMENTS = """\((\[[^]]*]),(\[[^]]*]),"([^"]*)""".toRegex()
    }
}
