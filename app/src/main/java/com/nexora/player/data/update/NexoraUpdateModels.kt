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
    /**
     * Final decision calculated by the app. It is true only when the remote
     * versionCode is greater than the installed BuildConfig.VERSION_CODE.
     */
    val available: Boolean,
    /**
     * Final decision calculated by the app. It is true only if an update is
     * really available and the server explicitly requires it.
     */
    val required: Boolean,
    val urls: RemoteUpdateUrls,
    val notifications: List<RemoteAppNotification> = emptyList(),
    val forceUpdate: Boolean = false,
    val minSupportedVersionCode: Int = 0,
    val serverAvailable: Boolean = false,
    val serverRequired: Boolean = false
)
