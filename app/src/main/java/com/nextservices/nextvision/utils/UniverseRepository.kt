package com.nextservices.nextvision.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class UniverseCollection(
    val serial: Int,
    val title: String,
    val id: Int,
    @SerializedName("backdropurl") val backdropUrl: String,
    val posterUrl: String?,
)

object UniverseRepository {
    private val gson = Gson()

    suspend fun load(context: Context): List<UniverseCollection> = withContext(Dispatchers.IO) {
        readList(context)
            ?.takeIf { collections ->
                collections.all { collection ->
                    !collection.posterUrl.isNullOrBlank() &&
                        collection.posterUrl != collection.backdropUrl
                }
            }
            ?.let { return@withContext it }

        val details = readAssetDetails(context)
        val entries = readAssetEntries(context)
        val collections = (entries?.mapNotNull { entry ->
            runCatching {
                val detail = details.firstOrNull { it.id == entry.id }
                UniverseCollection(
                    serial = entry.serial,
                    title = entry.title,
                    id = entry.id,
                    backdropUrl = entry.backdropUrl,
                    posterUrl = detail?.posterPath
                        ?: entry.posterUrl,
                )
            }.getOrNull()?.takeIf { !it.posterUrl.isNullOrBlank() }
        } ?: details.mapIndexedNotNull { index, detail ->
            detail.posterPath?.takeIf { it.isNotBlank() }?.let { posterPath ->
                UniverseCollection(
                    serial = index + 1,
                    title = detail.name,
                    id = detail.id,
                    backdropUrl = detail.backdropPath.orEmpty(),
                    posterUrl = posterPath,
                )
            }
        }).sortedBy { it.serial }
        if (collections.isEmpty()) error("Bundled collection data is unavailable")
        writeList(context, collections)
        collections
    }

    suspend fun detail(context: Context, id: Int): CachedCollectionDetail = withContext(Dispatchers.IO) {
        readAssetDetails(context).firstOrNull { it.id == id }
            ?: error("Bundled collection details unavailable for id $id")
    }

    private fun readList(context: Context): List<UniverseCollection>? = runCatching {
        val file = listFile(context)
        if (!file.exists()) return@runCatching null
        gson.fromJson<List<UniverseCollection>>(
            file.readText(),
            object : TypeToken<List<UniverseCollection>>() {}.type,
        )
    }.getOrNull()

    private fun writeList(context: Context, collections: List<UniverseCollection>) {
        listFile(context).apply {
            parentFile?.mkdirs()
            writeText(gson.toJson(collections))
        }
    }

    private fun readAssetEntries(context: Context): List<UniverseEntry>? = runCatching {
        gson.fromJson(
            context.assets.open("universe_collections.json").bufferedReader().use { it.readText() },
            Array<UniverseEntry>::class.java,
        ).sortedBy { it.serial }
    }.getOrNull()

    private fun readAssetDetails(context: Context): List<CachedCollectionDetail> = runCatching {
        gson.fromJson(
            context.assets.open("universe_collection_details.json").bufferedReader().use { it.readText() },
            Array<CachedCollectionDetail>::class.java,
        ).toList()
    }.getOrDefault(emptyList())

    private fun listFile(context: Context) = File(context.filesDir, "universe-cache/collections.json")

    private fun detailFile(context: Context, id: Int) =
        File(context.filesDir, "universe-cache/collection-$id.json")

    data class CachedCollectionDetail(
        val id: Int,
        val name: String,
        val overview: String? = null,
        val posterPath: String? = null,
        val backdropPath: String? = null,
        val parts: List<CachedCollectionMovie> = emptyList(),
    )

    data class CachedCollectionMovie(
        val id: Int,
        val title: String,
        val overview: String? = null,
        val releaseDate: String? = null,
        val voteAverage: Double = 0.0,
        val posterPath: String? = null,
        val backdropPath: String? = null,
    )

    private data class UniverseEntry(
        val serial: Int,
        val title: String,
        val id: Int,
        @SerializedName("backdropurl") val backdropUrl: String,
        @SerializedName("posterurl") val posterUrl: String? = null,
    )
}