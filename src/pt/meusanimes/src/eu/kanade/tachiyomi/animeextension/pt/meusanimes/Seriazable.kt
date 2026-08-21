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
    val id: Int,
    val number: Int,
    val season: Int,
    val name: String,
    val title: String,
    val thumb: String,
    val backdrop_path: String,
    val slug: String,
    val url: String,
)
