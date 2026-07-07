package eu.kanade.tachiyomi.lib.hashvideoidextractor

import android.util.Base64
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES.decodeHex
import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/*
Tested on those sites:
animeshd.cloud
animes.rpmplay.me
animes.strp2p.com
animes.upns.online
cdn10.rpmhub.site
cdn1.rpmplay.online
tokusatsu.rpmvip.com
 */

class HashVideoIdExtractor(private val client: OkHttpClient) {

    fun videosFromUrl(url: String, headers: Headers): List<Video> {
        val videoId = url.substringAfter("#")

        val hex = client.newCall(GET("https://${url.toHttpUrl().host}/api/v1/download?id=$videoId"))
            .execute().body.string().trim()

        val iv = IV.encodeToByteArray()
        val key = KEY.encodeToByteArray()
        val cipherText = Base64.encodeToString(
            hex.decodeHex(),
            Base64.DEFAULT,
        )

        val videoUrl = CryptoAES.decrypt(cipherText, key, iv)
            .substringAfter("\"mp4\":\"")
            .substringBefore("\"")
            .replace("\\", "")

        val quality = videoUrl.split("/")[8].substringBefore(".")

        val videoHeaders = headers.newBuilder()
            .set("Referer", "https://${url.toHttpUrl().host}")
            .build()

        return listOf(
            Video(videoUrl, "#$quality", videoUrl, videoHeaders),
        )
    }

    companion object {
        private const val IV = "1234567890oiuytr"
        private const val KEY = "kiemtienmua911ca"
    }
}
