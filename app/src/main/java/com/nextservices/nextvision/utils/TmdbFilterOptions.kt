package com.nextservices.nextvision.utils

/** The three user-facing TMDB Discover filters shared by movies and TV listings. */
data class TmdbFilterOptions(
    val genres: Set<Int> = emptySet(),
    val sortBy: String = "popularity.desc",
    val year: Int? = null,
) {
    fun toQuery(isTv: Boolean, page: Int): Map<String, String> = buildMap {
        put("page", page.toString())
        put("sort_by", sortBy)
        if (genres.isNotEmpty()) put("with_genres", genres.joinToString(","))
        year?.let { put(if (isTv) "first_air_date_year" else "primary_release_year", it.toString()) }
    }
}
