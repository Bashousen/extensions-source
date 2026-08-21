package eu.kanade.tachiyomi.animeextension.pt.meusanimes.extractors

import android.util.Base64
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.lib.unpacker.Unpacker
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class MeusAnimesExtractor(private val client: OkHttpClient, private val headers: Headers) {
    fun getVideosFromUrl(url: String, prefix: String): List<Video> {
        val playlistUtils by lazy { PlaylistUtils(client, headers) }

        val decodedUrl = decodeUrl(url)

        val targetUrl = client.newCall(GET(decodedUrl)).execute().body.string()
            .substringAfter("target = \"")
            .substringBefore("\"")

        val newHeaders = headers.newBuilder()
            .set("accept-language", "pt-BR,pt;q=0.8")
            .set("referer", "https://${decodedUrl.toHttpUrl().host}")
            .set("sec-fetch-dest", "document")
            .set("user-agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36")
            .build()

        val response = client.newCall(GET(targetUrl, newHeaders)).execute()
        val phpSessid = response.header("set-cookie")
            ?.substringAfter("=")
            ?.substringBefore(";")
        val redirectUrl = response.body.string()
            .substringAfter("url=")
            .substringBefore("\"")

        val sessidHeaders = headers.newBuilder()
            .set("Cookie", "PHPSESSID=$phpSessid")
            .build()

        val base64Bytes = client.newCall(GET(redirectUrl, sessidHeaders)).execute().body.string()
            .substringAfter("h=[")
            .substringBefore("]")
            .replace("\",\"", "")
            .trim('"')
            .hexToByteArray()

        val finalUrl = String(Base64.decode(base64Bytes, Base64.DEFAULT), Charsets.UTF_8)

        val doc = client.newCall(GET(finalUrl)).execute().asJsoup()
        val script = doc.selectFirst("script:containsData(function(p,a,c,k,e,d))")
            ?.data()
            ?.let { Unpacker.unpack(it) } ?: return emptyList()

        val playlistPath = script
            .substringAfter("\"videoUrl\":\"")
            .substringBefore("\"")
            .replace("\\", "")

        val playlistUrl = "https://${finalUrl.toHttpUrl().host}$playlistPath"

        return playlistUtils.extractFromHls(playlistUrl, videoNameGen = { quality -> "$prefix - $quality" })
    }

    private fun decodeUrl(url: String): String {
        val html = client.newCall(GET(url)).execute().body.string()
        val base64 = html
            .substringAfter("data-redir=\"")
            .substringBefore("\"")
        val jsArray = html
            .substringAfter("_kc = ")
            .substringBefore(";")

        val keyBytes = Json.decodeFromString<ByteArray>(jsArray)
        val decodedBytes = Base64.decode(base64, Base64.DEFAULT)
        val result = ByteArray(decodedBytes.size)

        for (i in decodedBytes.indices) {
            result[i] = (decodedBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }

        println("decodedUrl: ${String(result, Charsets.UTF_8)}")

        return String(result, Charsets.UTF_8)
    }
}
