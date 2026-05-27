package eu.kanade.tachiyomi.animeextension.pt.meusanimes

import kotlinx.serialization.Serializable

@Serializable
data class Data(
    val status: Boolean,
    val data: List<Animes>,
    val pagination: Pagination,
    val meta: Meta,
)

@Serializable
data class Pagination(
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int,
)

@Serializable
data class Meta(
    val currentPage: Int,
    val totalPages: Int,
    val totalItems: Int,
    val itemsPerPage: Int,
)

@Serializable
data class Animes(
    val name: String,
    val slug: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val background: String?,
    val runtime: Int?,
    val resolution: String,
    val animeName: String,
    val animeSlug: String,
    val animePoster: String,
    val animeBackground: String?,
    val animeRating: Float?,
    val animeAno: Int,
)
