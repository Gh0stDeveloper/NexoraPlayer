package com.nexora.player.data.update

data class RemoteReleaseNote(
    val id: String,
    val title: String,
    val description: String
)

data class RemoteAppNotification(
    val id: String,
    val title: String,
    val message: String,
    val enabled: Boolean = true,
    val createdAt: String = ""
)

data class RemoteVersionInfo(
    val versionName: String,
    val versionCode: Int,
    val releaseDate: String,
    val apkSizeBytes: Long? = null,
    val apkSha256: String? = null,
    val changelog: List<RemoteReleaseNote> = emptyList()
)

data class RemoteUpdateUrls(
    val download: String,
    val landing: String,
    val share: String
)

data class RemoteUpdateInfo(
    val packageName: String,
    val currentVersionCode: Int,
    val latestVersion: RemoteVersionInfo,
    val available: Boolean,
    val required: Boolean,
    val urls: RemoteUpdateUrls,
    val notifications: List<RemoteAppNotification> = emptyList()
)
