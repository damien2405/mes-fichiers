package com.damien.mesfichiers

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Entry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val isLink: Boolean = false,
    val childCount: Int = -1,
    val parentName: String? = null
)

sealed interface Loc {
    data class Local(val path: String) : Loc
    data class Ftp(val serverId: String, val path: String) : Loc
    data class Category(val cat: Cat) : Loc
}

enum class Cat(val label: String) {
    IMAGES("Images"),
    VIDEOS("Vidéos"),
    AUDIO("Audio"),
    DOCS("Docs"),
    APK("Fichiers d'installation")
}

enum class FileKind { IMAGE, VIDEO, AUDIO, PDF, DOC, SHEET, SLIDES, TEXT, ARCHIVE, APK, OTHER }

data class ImageRef(
    val serverId: String?,
    val path: String,
    val name: String,
    val size: Long,
    val modified: Long,
    val video: Boolean = false
) {
    val key: String get() = (serverId ?: "L") + "|" + path + "|" + size + "|" + modified
}

private val IMG_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
private val VID_EXT = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v", "ts")
private val AUD_EXT = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "amr")
private val DOC_EXT = setOf("doc", "docx", "odt", "rtf")
private val SHEET_EXT = setOf("xls", "xlsx", "ods", "csv")
private val SLIDE_EXT = setOf("ppt", "pptx", "odp")
private val TEXT_EXT = setOf("txt", "md", "log", "json", "xml", "html", "htm", "ini", "conf", "yml", "yaml")
private val ARC_EXT = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")

fun extOf(name: String): String = name.substringAfterLast('.', "").lowercase()

fun kindOf(name: String): FileKind {
    val e = extOf(name)
    return when {
        e in IMG_EXT -> FileKind.IMAGE
        e in VID_EXT -> FileKind.VIDEO
        e in AUD_EXT -> FileKind.AUDIO
        e == "pdf" -> FileKind.PDF
        e in DOC_EXT -> FileKind.DOC
        e in SHEET_EXT -> FileKind.SHEET
        e in SLIDE_EXT -> FileKind.SLIDES
        e in TEXT_EXT -> FileKind.TEXT
        e in ARC_EXT -> FileKind.ARCHIVE
        e == "apk" -> FileKind.APK
        else -> FileKind.OTHER
    }
}

fun mimeOf(name: String): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extOf(name)) ?: "application/octet-stream"

fun fmtSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes o"
    val units = arrayOf("Ko", "Mo", "Go", "To")
    var v = bytes.toDouble() / 1024.0
    var i = 0
    while (v >= 1024.0 && i < units.size - 1) {
        v /= 1024.0
        i++
    }
    val pattern = if (v >= 100.0) "%.0f %s" else "%.2f %s"
    return String.format(Locale.FRANCE, pattern, v, units[i])
}

fun fmtDate(ms: Long): String {
    if (ms <= 0L) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance()
    then.timeInMillis = ms
    val pattern = if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) "d MMM HH:mm" else "d MMM yyyy"
    return SimpleDateFormat(pattern, Locale.FRANCE).format(Date(ms))
}

fun sortEntries(list: List<Entry>, key: Int, asc: Boolean): List<Entry> {
    val cmp: Comparator<Entry> = when (key) {
        1 -> Comparator { a, b -> a.modified.compareTo(b.modified) }
        2 -> Comparator { a, b -> a.size.compareTo(b.size) }
        3 -> Comparator { a, b ->
            val c = extOf(a.name).compareTo(extOf(b.name))
            if (c != 0) c else String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name)
        }
        else -> Comparator { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name) }
    }
    val ordered = if (asc) cmp else cmp.reversed()
    val dirs = list.filter { it.isDir }.sortedWith(ordered)
    val files = list.filter { !it.isDir }.sortedWith(ordered)
    return dirs + files
}

// ---------------------------------------------------------------- stockage local

fun listLocal(path: String, showHidden: Boolean): List<Entry> {
    val files = File(path).listFiles() ?: throw IOException("Accès refusé ou dossier introuvable")
    val out = ArrayList<Entry>(files.size)
    for (f in files) {
        if (!showHidden && f.name.startsWith(".")) continue
        val dir = f.isDirectory
        val count = if (dir) (f.list()?.count { showHidden || !it.startsWith(".") } ?: 0) else -1
        out.add(Entry(f.name, f.path, dir, if (dir) 0L else f.length(), f.lastModified(), childCount = count))
    }
    return out
}

fun queryCategory(cat: Cat): List<Entry> {
    val ctx = AppModel.app
    val uri = MediaStore.Files.getContentUri("external")
    val cols = arrayOf(
        MediaStore.Files.FileColumns.DATA,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.DATE_MODIFIED
    )
    val selection: String
    val args: Array<String>
    when (cat) {
        Cat.IMAGES -> {
            selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=?"
            args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
        }
        Cat.VIDEOS -> {
            selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=?"
            args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
        }
        Cat.AUDIO -> {
            selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=?"
            args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString())
        }
        Cat.DOCS -> {
            val exts = listOf("pdf", "doc", "docx", "odt", "rtf", "xls", "xlsx", "ods", "csv", "ppt", "pptx", "odp", "txt")
            selection = exts.joinToString(" OR ", "(", ")") { MediaStore.Files.FileColumns.DATA + " LIKE ?" }
            args = exts.map { "%.$it" }.toTypedArray()
        }
        Cat.APK -> {
            selection = MediaStore.Files.FileColumns.DATA + " LIKE ?"
            args = arrayOf("%.apk")
        }
    }
    val out = ArrayList<Entry>()
    val cursor = ctx.contentResolver.query(uri, cols, selection, args, MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC")
    cursor?.use { c ->
        while (c.moveToNext()) {
            val path = c.getString(0) ?: continue
            if (path.contains("/.")) continue
            val name = c.getString(1) ?: File(path).name
            out.add(
                Entry(
                    name, path, false, c.getLong(2), c.getLong(3) * 1000L,
                    parentName = File(path).parentFile?.name
                )
            )
        }
    }
    return out
}

// ---------------------------------------------------------------- ouvrir / partager / enregistrer

fun toast(msg: String) {
    Toast.makeText(AppModel.app, msg, Toast.LENGTH_LONG).show()
}

fun openFile(ctx: Context, file: File, displayName: String = file.name) {
    try {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(uri, mimeOf(displayName))
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    } catch (e: Exception) {
        toast("Aucune application pour ouvrir ce fichier")
    }
}

fun shareFiles(ctx: Context, files: List<File>) {
    if (files.isEmpty()) return
    try {
        val uris = ArrayList(files.map { FileProvider.getUriForFile(ctx, ctx.packageName + ".files", it) })
        val i = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE)
        if (uris.size == 1) {
            i.putExtra(Intent.EXTRA_STREAM, uris[0])
            i.type = mimeOf(files[0].name)
        } else {
            i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            i.type = "*/*"
        }
        val clip = ClipData.newRawUri(null, uris[0])
        for (k in 1 until uris.size) clip.addItem(ClipData.Item(uris[k]))
        i.clipData = clip
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(i, null)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    } catch (e: Exception) {
        toast("Partage impossible : " + (e.message ?: ""))
    }
}

/** Copie un fichier dans le dossier Téléchargements (visible dans « Mes fichiers »). */
fun saveToDownloads(ctx: Context, src: File, name: String): Boolean {
    val values = ContentValues()
    values.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
    values.put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(name))
    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
    val out = ctx.contentResolver.openOutputStream(uri) ?: return false
    out.use { o -> src.inputStream().use { it.copyTo(o) } }
    return true
}
