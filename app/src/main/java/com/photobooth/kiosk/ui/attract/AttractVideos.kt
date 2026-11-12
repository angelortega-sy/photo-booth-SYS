package com.photobooth.kiosk.ui.attract

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.photobooth.kiosk.data.AttractSettingsStore
import java.io.File

/** One attract clip the booth can play: bundled in the APK or imported by an admin. */
internal data class AttractVideo(
    val id: String,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val imported: Boolean,
) {
    val sizeLabel: String
        get() = when {
            sizeBytes >= 1024L * 1024L -> String.format("%.1f MB", sizeBytes / 1048576.0)
            else -> "${sizeBytes / 1024L} KB"
        }
}

/** Where the clip lives, so the player knows how to open it. */
internal sealed interface AttractSource {
    data class Asset(val fileName: String) : AttractSource
    data class Stored(val file: File) : AttractSource
}

internal enum class AttractImportError { TOO_LARGE, UNREADABLE }

/**
 * Catalog of attract clips and the file operations behind Admin → Video
 * promocional. Imported clips are copied into app storage so moving or
 * deleting the original document on the device cannot break the booth.
 */
internal object AttractVideos {
    private const val ASSET_DIR = "attract"
    private const val STORE_DIR = "attract"
    private const val MAX_IMPORT_BYTES = 50L * 1024L * 1024L
    private const val THUMB_WIDTH = 162
    private const val THUMB_HEIGHT = 288

    fun storeDir(context: Context): File =
        File(context.filesDir, STORE_DIR).apply { mkdirs() }

    /** Bundled clips first (the default one on top), then imported, newest first. */
    fun list(context: Context): List<AttractVideo> {
        val bundled = runCatching {
            context.assets.list(ASSET_DIR)?.filter { it.endsWith(".mp4", true) }.orEmpty()
        }.getOrDefault(emptyList())
            .sortedWith(compareBy({ it != bundledDefaultName() }, { it }))
            .map { fileName -> describe(context, AttractSource.Asset(fileName)) }

        val imported = runCatching {
            storeDir(context).listFiles { file -> file.isFile && file.name.endsWith(".mp4", true) }
                .orEmpty()
                .sortedByDescending { it.lastModified() }
                .map { file -> describe(context, AttractSource.Stored(file)) }
        }.getOrDefault(emptyList())

        return (bundled + imported).filter { it.durationMs > 0L }
    }

    fun resolve(context: Context, videoId: String): AttractSource? = when {
        videoId.startsWith("asset:") -> {
            val name = videoId.removePrefix("asset:")
            AttractSource.Asset(name).takeIf { assetExists(context, name) }
        }

        videoId.startsWith("file:") -> {
            val file = File(storeDir(context), videoId.removePrefix("file:"))
            AttractSource.Stored(file).takeIf { file.isFile && file.length() > 0L }
        }

        else -> null
    }

    /** The clip to show for [videoId], falling back to the bundled default. */
    fun resolvedOrDefault(context: Context, videoId: String): AttractSource? =
        resolve(context, videoId) ?: resolve(context, AttractSettingsStore.DEFAULT_VIDEO_ID)

    fun idOf(source: AttractSource): String = when (source) {
        is AttractSource.Asset -> "asset:${source.fileName}"
        is AttractSource.Stored -> "file:${source.file.name}"
    }

    fun import(
        context: Context,
        uri: Uri,
        displayName: String?,
        onResult: (AttractVideo?, AttractImportError?) -> Unit,
    ) {
        val slug = sanitize(displayName)
        val target = File(storeDir(context), "promo_${System.currentTimeMillis()}_$slug.mp4")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_IMPORT_BYTES) error("too large")
                        output.write(buffer, 0, read)
                    }
                    total
                }
            } ?: error("no stream")
        }

        if (copied.isFailure) {
            runCatching { target.delete() }
            onResult(null, AttractImportError.TOO_LARGE)
            return
        }

        val video = describe(context, AttractSource.Stored(target))
        if (video.durationMs <= 0L) {
            runCatching { target.delete() }
            onResult(null, AttractImportError.UNREADABLE)
            return
        }
        onResult(video, null)
    }

    fun delete(context: Context, videoId: String): Boolean {
        val source = resolve(context, videoId) as? AttractSource.Stored ?: return false
        return runCatching { source.file.delete() }.getOrDefault(false)
    }

    /** Small preview of the first second, for the admin list. */
    fun thumbnail(context: Context, videoId: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        var descriptor: AssetFileDescriptor? = null
        return try {
            when (val source = resolve(context, videoId) ?: return null) {
                is AttractSource.Asset -> {
                    val fd = context.assets.openFd("$ASSET_DIR/${source.fileName}")
                    descriptor = fd
                    retriever.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                }

                is AttractSource.Stored -> retriever.setDataSource(source.file.absolutePath)
            }
            retrievePreview(retriever)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
            runCatching { descriptor?.close() }
        }
    }

    private fun retrievePreview(retriever: MediaMetadataRetriever): Bitmap? {
        val timeUs = 1_000_000L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                THUMB_WIDTH,
                THUMB_HEIGHT,
            )?.let { return it }
        }
        val full = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: return null
        return Bitmap.createScaledBitmap(full, THUMB_WIDTH, THUMB_HEIGHT, true)
    }

    private fun describe(context: Context, source: AttractSource): AttractVideo {
        val retriever = MediaMetadataRetriever()
        var descriptor: AssetFileDescriptor? = null
        val duration = try {
            when (source) {
                is AttractSource.Asset -> {
                    val fd = context.assets.openFd("$ASSET_DIR/${source.fileName}")
                    descriptor = fd
                    retriever.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                }

                is AttractSource.Stored -> retriever.setDataSource(source.file.absolutePath)
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: 0L
        } catch (_: Throwable) {
            0L
        } finally {
            runCatching { retriever.release() }
            runCatching { descriptor?.close() }
        }

        val size = when (source) {
            is AttractSource.Asset -> runCatching {
                context.assets.openFd("$ASSET_DIR/${source.fileName}").use { it.length }
            }.getOrDefault(0L)

            is AttractSource.Stored -> source.file.length()
        }

        return AttractVideo(
            id = idOf(source),
            name = when (source) {
                is AttractSource.Asset -> displayNameOf(source.fileName)
                is AttractSource.Stored -> displayNameOf(source.file.name)
            },
            durationMs = duration,
            sizeBytes = size,
            imported = source is AttractSource.Stored,
        )
    }

    private fun assetExists(context: Context, fileName: String): Boolean =
        runCatching { context.assets.openFd("$ASSET_DIR/$fileName").close() }.isSuccess

    private fun bundledDefaultName(): String =
        AttractSettingsStore.DEFAULT_VIDEO_ID.removePrefix("asset:")

    /** `promo_1712345678901_feria-de-mayo.mp4` → `Feria de mayo`. */
    private fun displayNameOf(fileName: String): String {
        val withoutExtension = fileName.substringBeforeLast('.').removePrefix("promo_")
        val withoutStamp = withoutExtension.replaceFirst(Regex("^\\d+_"), "")
        val cleaned = withoutStamp.replace('-', ' ').replace('_', ' ').trim()
        if (cleaned.isEmpty()) return "Video promocional"
        return cleaned.replaceFirstChar { it.uppercase() }
    }

    private fun sanitize(displayName: String?): String {
        val base = displayName?.substringBeforeLast('.')?.trim().orEmpty()
        val cleaned = base
            .replace(Regex("[^A-Za-z0-9]+"), "-")
            .trim('-')
            .lowercase()
            .take(40)
        return cleaned.ifEmpty { "video" }
    }
}
