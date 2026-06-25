package com.nexora.player.data.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class OnlineMusicRepository() {
    suspend fun search(query: String, limit: Int = 20): List<OnlineTrack> {
        val normalized = query.trim()
        if (normalized.isBlank()) return emptyList()

        return withContext(Dispatchers.IO) {
            val collected = mutableListOf<OnlineTrack>()

            OnlineMusicSources.providers
                .filter { it.enabled }
                .forEach { provider ->
                    val providerResults = runCatching {
                        when (provider.id) {
                            "jamendo" -> searchJamendo(normalized, limit)
                            "itunes" -> searchItunes(normalized, limit)
                            else -> emptyList()
                        }
                    }.getOrElse { emptyList() }

                    if (providerResults.isNotEmpty()) {
                        collected += providerResults
                    }
                }

            collected
                .distinctBy { it.key }
                .take(limit)
        }
    }

    private fun searchItunes(query: String, limit: Int): List<OnlineTrack> {
        val url = buildString {
            append(OnlineMusicSources.ITUNES_SEARCH_URL)
            append("?term=")
            append(URLEncoder.encode(query, Charsets.UTF_8.name()))
            append("&media=music&entity=song&limit=")
            append(limit.coerceIn(1, 50))
        }

        val json = httpGetJson(url) ?: return emptyList()
        val results = json.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val previewUrl = item.optString("previewUrl").takeIf { it.isNotBlank() } ?: continue
                val trackId = item.optLong("trackId", -1L).takeIf { it > 0L }?.toString() ?: continue
                add(
                    OnlineTrack(
                        providerId = "itunes",
                        providerLabel = "NexoraPlayerAPI",
                        sourceId = trackId,
                        title = item.optString("trackName").ifBlank { "Untitled" },
                        artist = item.optString("artistName"),
                        album = item.optString("collectionName"),
                        artworkUrl = item.optString("artworkUrl100").takeIf { it.isNotBlank() },
                        streamUrl = previewUrl,
                        downloadUrl = null,
                        durationMs = item.optLong("trackTimeMillis", 0L),
                        sourcePageUrl = item.optString("trackViewUrl").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    private fun searchJamendo(query: String, limit: Int): List<OnlineTrack> {
        val clientId = OnlineMusicSources.JAMENDO_CLIENT_ID.trim()
        if (clientId.isBlank()) return emptyList()

        val url = buildString {
            append(OnlineMusicSources.JAMENDO_TRACKS_URL)
            append("?client_id=")
            append(URLEncoder.encode(clientId, Charsets.UTF_8.name()))
            append("&format=json&limit=")
            append(limit.coerceIn(1, 50))
            append("&namesearch=")
            append(URLEncoder.encode(query, Charsets.UTF_8.name()))
            append("&audioformat=mp32&audiodlformat=mp32&imagesize=300&include=musicinfo")
        }

        val json = httpGetJson(url) ?: return emptyList()
        val results = json.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val audioUrl = item.optString("audio").takeIf { it.isNotBlank() } ?: continue
                val sourceId = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                add(
                    OnlineTrack(
                        providerId = "jamendo",
                        providerLabel = "NexoraPlayerAPI",
                        sourceId = sourceId,
                        title = item.optString("name").ifBlank { "Untitled" },
                        artist = item.optString("artist_name"),
                        album = item.optString("album_name"),
                        artworkUrl = item.optString("image").takeIf { it.isNotBlank() },
                        streamUrl = audioUrl,
                        downloadUrl = item.optString("audiodownload").takeIf { it.isNotBlank() },
                        durationMs = item.optLong("duration", 0L) * 1000L,
                        sourcePageUrl = item.optString("shareurl").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    private fun httpGetJson(url: String): JSONObject? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(response)
        } catch (_: Throwable) {
            runCatching {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (error.isNotBlank()) JSONObject(error) else null
            }.getOrNull()
        } finally {
            connection.disconnect()
        }
    }
}
