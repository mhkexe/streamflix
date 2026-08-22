package com.nextservices.nextvision.utils

import com.nextservices.nextvision.models.Category
import com.nextservices.nextvision.models.Movie
import com.nextservices.nextvision.models.TvShow

object StartupPreloadStore {
    data class Data(
        val home: List<Category>?,
        val movies: List<Movie>?,
        val tvShows: List<TvShow>?,
    )

    @Volatile
    private var dataByProvider: Map<String, Data> = emptyMap()

    fun providerKey(provider: com.nextservices.nextvision.providers.Provider): String =
        provider.name + "__" + provider.baseUrl.trim().trimEnd('/')

    fun put(provider: com.nextservices.nextvision.providers.Provider, data: Data) {
        dataByProvider = dataByProvider + (providerKey(provider) to data)
    }

    fun get(provider: com.nextservices.nextvision.providers.Provider): Data? =
        dataByProvider[providerKey(provider)]
}