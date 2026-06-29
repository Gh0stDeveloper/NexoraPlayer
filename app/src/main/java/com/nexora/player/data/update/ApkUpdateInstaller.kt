package com.nexora.player.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.nexora.player.BuildConfig
import com.nexora.player.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.math.roundToInt

class ApkUpdateInstaller(private val context: Context) {
    private val _state = MutableStateFlow(UpdateInstallState())
    val state: StateFlow<UpdateInstallState> = _state

    suspend fun downloadAndOpenInstaller(info: RemoteUpdateInfo) = withContext(Dispatchers.IO) {
        val downloadUrl = info.urls.download.ifBlank { "${BuildConfig.NEXORA_SERVER_URL}/api/download/latest" }
        val expectedSize = info.latestVersion.apkSizeBytes
        val expectedSha256 = info.latestVersion.apkSha256?.lowercase()?.takeIf { it.isNotBlank() }
        val fileName = buildApkFileName(info)
        val updatesDir = File(context.externalCacheDir ?: context.cacheDir, "updates").apply { mkdirs() }
        val outFile = File(updatesDir, fileName)

        _state.value = UpdateInstallState(
            active = true,
            downloading = true,
            progressPercent = 0,
            totalBytes = expectedSize,
            message = context.getString(R.string.apk_preparing_download)
        )

        runCatching {
            download(downloadUrl, outFile, expectedSize)
            validate(outFile, expectedSize, expectedSha256)
            validateDownloadedApk(outFile, info)
            _state.value = _state.value.copy(
                downloading = false,
                progressPercent = 100,
                downloadedBytes = outFile.length(),
                totalBytes = outFile.length(),
                message = context.getString(R.string.apk_downloaded_opening),
                error = null
            )
            openInstaller(outFile)
        }.onFailure { throwable ->
            _state.value = UpdateInstallState(
                active = true,
                downloading = false,
                message = null,
                error = throwable.message ?: context.getString(R.string.apk_download_error)
            )
        }
    }

    fun clear() {
        _state.value = UpdateInstallState()
    }

    private fun download(downloadUrl: String, outFile: File, expectedSize: Long?) {
        val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.android.package-archive,*/*")
            setRequestProperty("User-Agent", "NexoraPlayer/${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException(context.getString(R.string.apk_server_error, code, error))
            }
            val total = connection.contentLengthLong.takeIf { it > 0L } ?: expectedSize
            var readTotal = 0L
            connection.inputStream.use { input ->
                FileOutputStream(outFile, false).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        readTotal += read
                        val progress = if (total != null && total > 0L) {
                            ((readTotal * 100.0) / total).roundToInt().coerceIn(0, 99)
                        } else 0
                        _state.value = UpdateInstallState(
                            active = true,
                            downloading = true,
                            progressPercent = progress,
                            downloadedBytes = readTotal,
                            totalBytes = total,
                            message = context.getString(R.string.apk_downloading)
                        )
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun validate(file: File, expectedSize: Long?, expectedSha256: String?) {
        if (!file.exists() || file.length() <= 0L) {
            throw IllegalStateException(context.getString(R.string.apk_empty))
        }
        if (expectedSize != null && expectedSize > 0L) {
            val delta = kotlin.math.abs(file.length() - expectedSize)
            if (delta > 1024L * 1024L) {
                throw IllegalStateException(context.getString(R.string.apk_size_mismatch))
            }
        }
        if (!expectedSha256.isNullOrBlank()) {
            val digest = sha256(file)
            if (digest != expectedSha256) {
                throw IllegalStateException(context.getString(R.string.apk_sha_failed))
            }
        }
    }


    @Suppress("DEPRECATION")
    private fun validateDownloadedApk(file: File, info: RemoteUpdateInfo) {
        val packageInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: throw IllegalStateException(context.getString(R.string.apk_invalid_package))

        if (packageInfo.packageName != BuildConfig.APPLICATION_ID) {
            throw IllegalStateException(
                context.getString(
                    R.string.apk_package_mismatch,
                    BuildConfig.APPLICATION_ID,
                    packageInfo.packageName ?: "unknown"
                )
            )
        }

        val apkVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
        val installedVersionCode = BuildConfig.VERSION_CODE.toLong()

        if (apkVersionCode <= installedVersionCode) {
            throw IllegalStateException(
                context.getString(
                    R.string.apk_not_newer,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                    packageInfo.versionName ?: "unknown",
                    apkVersionCode
                )
            )
        }

        val serverVersionCode = info.latestVersion.versionCode.toLong()
        if (serverVersionCode > 0L && apkVersionCode != serverVersionCode) {
            throw IllegalStateException(
                context.getString(
                    R.string.apk_version_mismatch,
                    serverVersionCode,
                    apkVersionCode
                )
            )
        }
    }

    private fun openInstaller(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            _state.value = _state.value.copy(
                downloading = false,
                waitingForInstallPermission = true,
                message = context.getString(R.string.apk_authorize_install),
                error = null
            )
            val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(permissionIntent)
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun buildApkFileName(info: RemoteUpdateInfo): String {
        val raw = "Nexora-Player-${info.latestVersion.versionName}.apk"
        return raw.replace(Regex("[^A-Za-z0-9._-]"), "-").let {
            if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk"
        }
    }
}
