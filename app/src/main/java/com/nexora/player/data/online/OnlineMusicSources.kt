package com.nexora.player.data.online

object OnlineMusicSources {
    const val ITUNES_SEARCH_URL = "https://itunes.apple.com/search"
    const val JAMENDO_TRACKS_URL = "https://api.jamendo.com/v3.0/tracks/"

    // Edita este archivo para añadir o desactivar proveedores futuros.
    // iTunes Search API: no requiere clave y devuelve previewUrl + artwork.
    // Jamendo: requiere client_id gratuito; devuelve audio, audiodownload e imagen.
    // Audius: docs.audius.org/api
    // Deezer: developers.deezer.com/api
    data class ProviderEntry(
        val id: String,
        val label: String,
        val enabled: Boolean = true
    )

    const val JAMENDO_CLIENT_ID = ""

    val providers: List<ProviderEntry> = listOf(
        ProviderEntry(id = "itunes", label = "iTunes Search API", enabled = true),
        ProviderEntry(id = "jamendo", label = "Jamendo", enabled = JAMENDO_CLIENT_ID.isNotBlank())
    )
}
