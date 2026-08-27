package eu.kanade.tachiyomi.animeextension.pt.meusanimes

import kotlinx.serialization.Serializable

@Serializable
data class Anime(
    val data: Data,
)

@Serializable
data class Data(
    val episodes: List<Episode>,
)

@Serializable
data class Episode(
    val number: Int,
    val season: Int,
    val url: String,
)
