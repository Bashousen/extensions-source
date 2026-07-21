package eu.kanade.tachiyomi.lib.abyssextractor

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

class AbyssExtractor(private val client: OkHttpClient) {
    private val cryptoHelper by lazy { CryptoHelper() }

    fun videosFromUrl(url: String, headers: Headers): List<Video> {
        val html = client.newCall(GET(url)).execute().body!!.string()
        val medias = parseEncryptedMp4MetadataFromHtml(html) ?: return emptyList()

        if (medias.sources == null) {
            Log.e("", "No media sources found")
            return emptyList()
        }

        val videoHeaders = headers.newBuilder().set("Referer", "https://${url.toHttpUrl().host}/").build()

        return medias.sources.mapNotNull { source ->
            val quality = source?.label ?: return@mapNotNull null
            val simpleVideo = medias.toSimpleVideo(quality)
            val token = getToken(simpleVideo) ?: return@mapNotNull null

            val videoUrl = "${simpleVideo.url}/sora/${simpleVideo.size}/$token"
            Video(videoUrl, "Abyss - $quality", videoUrl, videoHeaders)
        }
    }

    private fun parseEncryptedMp4MetadataFromHtml(html: String): Mp4? {
        val jsCode = Jsoup.parse(html)
            .select("script")
            .find { it.html().contains("datas") }
            ?.html()

        if (jsCode == null) {
            Log.e("", "No encoded media metadata found in the provided HTML.")
            return null
        }
        val datasRegex = Regex("""const\s+datas\s*=\s*"([^"]*)"""")
        val datas = datasRegex.find(jsCode)?.groups?.get(1)?.value
        val decodedDatas = String(Base64.decode(datas, Base64.DEFAULT), Charsets.ISO_8859_1)
        val mediaMetadata = decodedDatas.toObject<Datas>()
        val encryptedMediaMetadata = mediaMetadata.media

        if (encryptedMediaMetadata == null) {
            Log.e("", "failed to get encrypted media")
            return null
        }

        val mediaKey = "${mediaMetadata.user_id}:${mediaMetadata.slug}:${mediaMetadata.md5_id}"
        val decryptionKey = cryptoHelper.getKey(mediaKey).toByteArray()
        val mediaSources = cryptoHelper.decryptString(encryptedMediaMetadata, decryptionKey)

        return mediaSources.toObject<Videos>()
            .mp4?.copy(
                slug = mediaMetadata.slug,
                md5_id = mediaMetadata.md5_id,
            )
    }

    private fun getToken(simpleVideo: SimpleVideo): String? {
        if (simpleVideo.size == null) {
            Log.e("", "No video size found")
            return null
        }

        val encryptionKey = cryptoHelper.getKey(simpleVideo.size)
        val path = "/mp4/${simpleVideo.md5_id}/${simpleVideo.resId}/${simpleVideo.size}?v=${simpleVideo.slug}"
        val encryptedBody = cryptoHelper.encryptAESCTR(path, encryptionKey)
        val token = doubleEncodeToBase64(encryptedBody)

        return token
    }

    private fun doubleEncodeToBase64(input: String): String {
        val first = Base64
            .encodeToString(input.toByteArray(Charsets.ISO_8859_1), Base64.DEFAULT)
            .replace("=", "")

        return Base64
            .encodeToString(first.toByteArray(), Base64.DEFAULT)
            .replace("=", "")
    }

    private inline fun <reified T> String.toObject(): T {
        return Gson().fromJson(this, T::class.java)
    }
}
