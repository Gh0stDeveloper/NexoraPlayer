package com.nexora.player.data.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.images.ImageFormats
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Utility for actually writing metadata into audio files on disk.
 *
 * Flow:
 *  1. Copy the original file to the app cache (full write access, no permissions needed).
 *  2. Edit the copy with JAudioTagger (modifies ID3v2 / Vorbis / MP4 tags in the copy).
 *  3. Write the modified copy back to the original URI:
 *       – Android 11+: caller first requests MediaStore.createWriteRequest, then calls
 *         [commitEdit] from the launcher callback once the user approves.
 *       – Android 9 and below: [editAndCommit] writes directly via openFileDescriptor.
 *  4. Update the MediaStore database so the player sees the new metadata immediately.
 *
 * Dependency required in app/build.gradle:
 *   implementation("net.jthink:jaudiotagger:3.0.1")
 */
object AudioFileEditor {

    init {
        // JAudioTagger's verbose logging is unnecessary on Android
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    // ── Public data ───────────────────────────────────────────────────────────

    /** Holds the prepared (JAudioTagger-edited) temp file until write-back is confirmed. */
    data class PreparedEdit(
        val tempFile: File,
        val title:    String,
        val artist:   String,
        val album:    String
    )

    /** Result after a complete save cycle. */
    sealed class EditResult {
        object Success : EditResult()
        data class Error(val message: String, val cause: Throwable? = null) : EditResult()
        /** Caller must launch [intentSender] via MediaStore.createWriteRequest. */
        data class NeedsWritePermission(
            val prepared:     PreparedEdit,
            val intentSender: android.content.IntentSender
        ) : EditResult()
    }

    // ── API ───────────────────────────────────────────────────────────────────

    /**
     * Main entry point. Call from a coroutine; does IO on [Dispatchers.IO].
     *
     * On Android 11+ this returns [EditResult.NeedsWritePermission] — the caller
     * must present the system dialog and call [commitEdit] afterwards.
     *
     * On Android 9 and below it writes directly and returns [EditResult.Success]
     * or [EditResult.Error].
     */
    suspend fun prepareAndEdit(
        context:       Context,
        uri:           Uri,
        title:         String,
        artist:        String,
        album:         String,
        artworkBitmap: Bitmap? = null
    ): EditResult = withContext(Dispatchers.IO) {

        // 1. Copy original file to cache
        val prepared = runCatching {
            prepareEdit(context, uri, title, artist, album, artworkBitmap)
        }.getOrElse { e ->
            return@withContext EditResult.Error(
                "No se pudo leer el archivo de audio.", e
            )
        }

        // 2. Write back — strategy depends on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: need user approval to write media files
            val result = runCatching {
                val req = MediaStore.createWriteRequest(
                    context.contentResolver, listOf(uri)
                )
                EditResult.NeedsWritePermission(prepared, req.intentSender)
            }.getOrElse { e ->
                prepared.tempFile.delete()
                EditResult.Error("No se pudo solicitar permiso de escritura.", e)
            }
            return@withContext result

        } else {
            // Android 9 and below: write directly via file descriptor
            return@withContext commitEdit(context, uri, prepared)
        }
    }

    /**
     * Call this from the [ActivityResultContracts.StartIntentSenderForResult] callback
     * after the user grants write permission (Android 11+).
     *
     * [prepared] is the object returned inside [EditResult.NeedsWritePermission].
     */
    suspend fun commitEdit(
        context:  Context,
        uri:      Uri,
        prepared: PreparedEdit
    ): EditResult = withContext(Dispatchers.IO) {
        runCatching {
            // Write modified file back to original URI via file descriptor
            context.contentResolver.openFileDescriptor(uri, "rwt")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { out ->
                    FileInputStream(prepared.tempFile).use { input ->
                        input.copyTo(out)
                    }
                }
            } ?: error("No se pudo abrir el archivo para escritura.")

            // Update MediaStore database (display values & search index)
            val cv = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE,         prepared.title)
                put(MediaStore.Audio.Media.ARTIST,        prepared.artist)
                put(MediaStore.Audio.Media.ALBUM,         prepared.album)
                put(MediaStore.Audio.Media.DATE_MODIFIED,
                    System.currentTimeMillis() / 1_000L)
            }
            context.contentResolver.update(uri, cv, null, null)

            EditResult.Success
        }.getOrElse { e ->
            EditResult.Error("No se pudo guardar el archivo.", e)
        }.also {
            prepared.tempFile.delete()
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /**
     * Copies the audio file to cache, edits its tags with JAudioTagger,
     * and returns a [PreparedEdit] pointing to the modified temp file.
     *
     * Must be called on an IO dispatcher.
     */
    private fun prepareEdit(
        context:       Context,
        uri:           Uri,
        title:         String,
        artist:        String,
        album:         String,
        artworkBitmap: Bitmap?
    ): PreparedEdit {
        val mimeType  = context.contentResolver.getType(uri) ?: "audio/mpeg"
        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType) ?: "mp3"

        // Copy original to cache — JAudioTagger needs a real File, not a URI
        val tempFile = File(context.cacheDir, "nexora_edit_${System.currentTimeMillis()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { input.copyTo(it) }
        } ?: error("No se pudo abrir el archivo de origen.")

        // Edit tags
        try {
            val audioFile = AudioFileIO.read(tempFile)
            val tag       = audioFile.tagOrCreateAndSetDefault

            tag.setField(FieldKey.TITLE,  title.trim().ifBlank { " " })
            tag.setField(FieldKey.ARTIST, artist.trim().ifBlank { " " })
            tag.setField(FieldKey.ALBUM,  album.trim().ifBlank { " " })

            if (artworkBitmap != null) {
                val bytes = ByteArrayOutputStream().also { bos ->
                    artworkBitmap.compress(Bitmap.CompressFormat.JPEG, 90, bos)
                }.toByteArray()

                // Build artwork without .apply{} to avoid Kotlin val/setter ambiguity
                val artwork = ArtworkFactory.getNew()
                artwork.binaryData  = bytes
                artwork.mimeType    = ImageFormats.getMimeTypeForBinarySignature(bytes)
                    ?: "image/jpeg"
                artwork.pictureType = 3  // 3 = Front Cover (ID3 standard, replaces PictureTypes.DEFAULT_ID)
                tag.deleteArtworkField()
                tag.setField(artwork)
            }

            AudioFileIO.write(audioFile)
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }

        return PreparedEdit(tempFile, title, artist, album)
    }
}
