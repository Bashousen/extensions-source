package eu.kanade.tachiyomi.lib.abyssextractor

data class Videos(
    val mp4: Mp4? = null,
)

data class Mp4(
    val domains: List<String?>? = null,
    val sources: List<Source?>? = null,
    val slug: String? = null,
    val md5_id: Int? = null,
)

fun Mp4.toSimpleVideo(resolution: String): SimpleVideo {
    val source = sources?.find { it?.label == resolution && it.sub != null }
    return SimpleVideo(
        slug = slug,
        md5_id = md5_id,
        label = source?.label,
        size = source?.size,
        url = buildSegmentUrl(domains?.firstOrNull(), source?.sub),
        path = source?.path,
        resId = source?.res_id,
    )
}

fun buildSegmentUrl(domain: String?, subdomain: String?): String {
    return "https://$subdomain.${domain?.substringAfter(".")}"
}

data class SimpleVideo(
    val slug: String? = null,
    val md5_id: Int? = null,
    val label: String? = null,
    val size: Long? = null,
    val url: String? = null,
    val path: String? = null,
    val resId: Int? = null,
)

data class Datas(
    val md5_id: Int? = null,
    val media: String? = null,
    val slug: String? = null,
    val user_id: Int? = null,
)

data class Source(
    val codec: String? = null,
    val label: String? = null,
    val partSize: Int? = null,
    val path: String? = null,
    val res_id: Int? = null,
    val size: Long? = null,
    val status: Boolean? = null,
    val sub: String? = null,
    val url: String? = null,
)
