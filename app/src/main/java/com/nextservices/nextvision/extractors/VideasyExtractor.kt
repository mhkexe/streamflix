package com.nextservices.nextvision.extractors

import com.nextservices.nextvision.models.Video
import androidx.media3.common.MimeTypes
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class VideasyExtractor : Extractor() {
    override val name = "Videasy"
    override val mainUrl = "https://api.speedracelight.com"

    data class ServerConfig(
        val name: String,
        val endpoint: String,
        val movieOnly: Boolean = false
    )

    private val englishServers = listOf(
        ServerConfig("Neon", "mb-flix"),
        ServerConfig("Yoru", "cdn", movieOnly = true),
        ServerConfig("Cypher", "downloader2"),
        ServerConfig("Sage", "1movies"),
        ServerConfig("Breach", "m4uhd"),
        ServerConfig("Vyse", "hdmovie")
    )

    fun servers(videoType: Video.Type, language: String): List<Video.Server> {
        return when (language) {
            "en" -> {
                englishServers.mapNotNull { config ->
                    if (config.movieOnly && videoType !is Video.Type.Movie) return@mapNotNull null
                    
                    val url = when (videoType) {
                        is Video.Type.Movie -> {
                            val year = videoType.releaseDate.split("-").firstOrNull() ?: ""
                            "$mainUrl/${config.endpoint}/sources-with-title?title=${videoType.title}&mediaType=movie&year=$year&tmdbId=${videoType.id}&imdbId=${videoType.imdbId ?: ""}&enc=2"
                        }
                        is Video.Type.Episode -> {
                            val year = videoType.tvShow.releaseDate?.split("-")?.firstOrNull() ?: ""
                            "$mainUrl/${config.endpoint}/sources-with-title?title=${videoType.tvShow.title}&mediaType=tv&year=$year&tmdbId=${videoType.tvShow.id}&imdbId=${videoType.tvShow.imdbId ?: ""}&episodeId=${videoType.number}&seasonId=${videoType.season.number}&enc=2"
                        }
                    }
                    
                    Video.Server(
                        id = "${config.name} (Videasy)",
                        name = "${config.name} (Videasy)",
                        src = url
                    )
                }
            }
            else -> {
                val serverName = when (language) {
                    "de" -> "Killjoy (Videasy)"
                    else -> return emptyList()
                }
                
                val videasyLang = when (language) {
                    "de" -> "german"
                    else -> return emptyList()
                }

                val endpoint = "meine"

                val url = when (videoType) {
                    is Video.Type.Movie -> {
                        val year = videoType.releaseDate.split("-").firstOrNull() ?: ""
                        "$mainUrl/$endpoint/sources-with-title?title=${videoType.title}&mediaType=movie&year=$year&tmdbId=${videoType.id}&imdbId=${videoType.imdbId ?: ""}&language=$videasyLang&enc=2"
                    }
                    is Video.Type.Episode -> {
                        val year = videoType.tvShow.releaseDate?.split("-")?.firstOrNull() ?: ""
                        "$mainUrl/$endpoint/sources-with-title?title=${videoType.tvShow.title}&mediaType=tv&year=$year&tmdbId=${videoType.tvShow.id}&imdbId=${videoType.tvShow.imdbId ?: ""}&episodeId=${videoType.number}&seasonId=${videoType.season.number}&language=$videasyLang&enc=2"
                    }
                }

                listOf(Video.Server(
                    id = serverName,
                    name = serverName,
                    src = url
                ))
            }
        }
    }

    fun server(videoType: Video.Type, language: String): Video.Server? {
        return servers(videoType, language).firstOrNull()
    }

    override suspend fun extract(link: String): Video {
        val client = OkHttpClient()

        // 1. Extract IDs from link
        val url = link.toHttpUrlOrNull() ?: throw Exception("Invalid link")
        val tmdbId = url.queryParameter("tmdbId") ?: ""
        
        // 2. Get Seed
        val seedRequest = Request.Builder()
            .url("$mainUrl/seed?mediaId=$tmdbId")
            .header("Accept", "*/*")
            .header("Origin", "https://player.videasy.to")
            .header("Referer", "https://player.videasy.to/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
            .build()
        
        val seedResponse = client.newCall(seedRequest).execute()
        val seedBody = seedResponse.body?.string() ?: throw Exception("Failed to get seed")
        val seed = JSONObject(seedBody).optString("seed")

        // 3. Get encrypted data from API with seed
        val finalLink = if (link.contains("seed=")) link else "$link&seed=$seed"
        val request = Request.Builder()
            .url(finalLink)
            .header("Accept", "*/*")
            .header("Origin", "https://player.videasy.to")
            .header("Referer", "https://player.videasy.to/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
            .build()
        
        val response = client.newCall(request).execute()
        val encData = response.body?.string() ?: throw Exception("Failed to get encrypted data")

        // 4. Post to decryption API
        val json = JSONObject()
        json.put("text", encData)
        json.put("id", tmdbId)
        json.put("seed", seed)

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val decRequest = Request.Builder()
            .url("https://enc-dec.app/api/dec-videasy")
            .post(body)
            .build()
        
        val decResponse = client.newCall(decRequest).execute()
        val decBody = decResponse.body?.string() ?: "{}"
        val decJson = JSONObject(decBody)
        val result = decJson.optString("result")

        // 5. Parse result (JSON string containing sources)
        val resultJson = JSONObject(result)
        val sources = resultJson.optJSONArray("sources")
        val subtitles = mutableListOf<Video.Subtitle>()
        
        val tracks = resultJson.optJSONArray("subtitles")
        if (tracks != null) {
            for (i in 0 until tracks.length()) {
                val track = tracks.getJSONObject(i)
                val label = track.optString("lang", "Unknown")
                val subtitleUrl = track.optString("url")
                if (subtitleUrl.isNotEmpty()) {
                    subtitles.add(Video.Subtitle(
                        label = label,
                        file = subtitleUrl
                    ))
                }
            }
        }

        if (sources != null && sources.length() > 0) {
            val source = sources.getJSONObject(0)
            
            // Find ServerConfig based on endpoint in URL
            val config = englishServers.find { link.contains(it.endpoint) }
            
            // Reyna and Cypher use MP4 instead of HLS
            val isMp4Server = config?.name == "Reyna" || config?.name == "Cypher"
            val mimeType = if (isMp4Server) MimeTypes.VIDEO_MP4 else MimeTypes.APPLICATION_M3U8

            return Video(
                source = source.optString("url"),
                type = mimeType,
                subtitles = subtitles,
                headers = mapOf("Referer" to "https://player.videasy.to/")
            )
        }

        throw Exception("No video source found")
    }
}
