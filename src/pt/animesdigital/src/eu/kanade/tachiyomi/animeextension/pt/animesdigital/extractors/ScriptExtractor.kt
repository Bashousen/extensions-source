package eu.kanade.tachiyomi.animeextension.pt.animesdigital.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.m3u8server.M3u8Integration
import eu.kanade.tachiyomi.lib.unpacker.Unpacker
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object ScriptExtractor {
    fun videosFromScript(scriptData: String, headers: Headers): List<Video> {
        val clientIgnoringSSL by lazy { OkHttpClient.Builder().ignoreAllSSLErrors().build() }
        val m3u8Integration by lazy { M3u8Integration(clientIgnoringSSL) }

        val script = when {
            "eval(function" in scriptData -> Unpacker.unpack(scriptData)
            else -> scriptData
        }.ifEmpty { null }?.replace("\\", "") ?: return emptyList()

        return script.substringAfter("sources:").substringAfter(".src(")
            .substringBefore(")")
            .substringAfter("[")
            .substringBefore("]")
            .split("{")
            .drop(1)
            .flatMap {
                val quality = it.substringAfter("label", "")
                    .substringAfterKey()
                    .trim()
                    .ifEmpty { "Animes Digital" }
                val url = it.substringAfter("file").substringAfter("src")
                    .substringAfterKey()
                    .trim()

                when {
                    "cdn.imagesskill.com" in url -> {
                        //Dantotsu por padrao defini para "br, gzip", com isso setado nao retorna um m3u8 valido.
                        val newHeaders = headers.newBuilder().set("accept-encoding", "").build()
                        m3u8Integration.processVideoList(listOf(Video(url, quality, url, newHeaders)))
                    }

                    else -> listOf(Video(url, quality, url, headers))
                }
            }
    }

    private fun OkHttpClient.Builder.ignoreAllSSLErrors(): OkHttpClient.Builder {
        val naiveTrustManager =
            @Suppress("CustomX509TrustManager")
            object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
            }

        val insecureSocketFactory = SSLContext.getInstance("TLSv1.2").apply {
            val trustAllCerts = arrayOf<TrustManager>(naiveTrustManager)
            init(null, trustAllCerts, SecureRandom())
        }.socketFactory

        sslSocketFactory(insecureSocketFactory, naiveTrustManager)
        hostnameVerifier { _, _ -> true }
        return this
    }

    private fun String.substringAfterKey() = substringAfter(':')
        .substringAfter('"')
        .substringBefore('"')
        .substringAfter("'")
        .substringBefore("'")
}
