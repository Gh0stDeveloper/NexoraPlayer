package com.nexora.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexora.player.R
import com.nexora.player.data.update.RemoteUpdateInfo
import com.nexora.player.data.update.UpdateInstallState
import kotlin.math.max

@Suppress("UNUSED_PARAMETER")
@Composable
fun ReleaseNotesDialog(
    updateInfo: RemoteUpdateInfo,
    onDownload: () -> Unit,
    onLater: () -> Unit,
    installState: UpdateInstallState = UpdateInstallState(),
    onOpenInBrowser: () -> Unit = {},
    onAuthorizeInstallPermission: () -> Unit = {},
    onClearInstallMessage: () -> Unit = {}
) {
    val latest = updateInfo.latestVersion
    AlertDialog(
        onDismissRequest = {
            if (!updateInfo.required) onLater()
        },
        icon = {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFFE64366), Color(0xFFFF9500)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (updateInfo.required) Icons.Filled.NewReleases else Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = Color.White
                )
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (updateInfo.required) stringResource(R.string.release_required_title) else stringResource(R.string.release_available_title),
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        "Nexora Player ${latest.versionName} · build ${latest.versionCode}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val size = latest.apkSizeBytes?.let { formatApkSize(it) }
                Text(
                    if (updateInfo.required) {
                        stringResource(R.string.release_required_body)
                    } else {
                        stringResource(R.string.release_available_body)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!size.isNullOrBlank() || latest.releaseDate.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (size != null) Text("APK: $size", style = MaterialTheme.typography.bodySmall)
                            if (latest.releaseDate.isNotBlank()) Text(stringResource(R.string.release_date, latest.releaseDate), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                val notes = latest.changelog.take(5)
                if (notes.isEmpty()) {
                    ReleaseNoteRow(Icons.Filled.AutoAwesome, stringResource(R.string.release_recent_title), stringResource(R.string.release_recent_body))
                } else {
                    notes.forEach { note ->
                        ReleaseNoteRow(
                            icon = Icons.Filled.AutoAwesome,
                            title = note.title,
                            body = note.description.ifBlank { stringResource(R.string.release_note_fallback) }
                        )
                    }
                }
                if (installState.active) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                installState.error ?: installState.message ?: stringResource(R.string.release_preparing),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (installState.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            if (installState.downloading) {
                                LinearProgressIndicator(
                                    progress = { installState.progressPercent / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    "${installState.progressPercent}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (installState.waitingForInstallPermission) {
                                Text(
                                    stringResource(R.string.release_install_permission_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (installState.error != null) {
                                FilledTonalButton(onClick = onOpenInBrowser) { Text(stringResource(R.string.release_open_browser)) }
                            }
                        }
                    }
                }

                if (updateInfo.required) {
                    Text(
                        stringResource(R.string.release_required_no_later),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            val waitingForPermission = installState.waitingForInstallPermission
            Button(
                onClick = if (waitingForPermission) onAuthorizeInstallPermission else onDownload,
                enabled = !installState.downloading
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    when {
                        installState.downloading -> stringResource(R.string.release_downloading)
                        waitingForPermission -> stringResource(R.string.release_authorize_install)
                        else -> stringResource(R.string.release_download)
                    }
                )
            }
        },
        dismissButton = {
            if (!updateInfo.required) {
                TextButton(onClick = onLater) { Text(stringResource(R.string.release_later)) }
            }
        }
    )
}

@Composable
private fun ReleaseNoteRow(
    icon: ImageVector,
    title: String,
    body: String
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatApkSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return "%.1f MB".format(max(0.1, mb))
}
