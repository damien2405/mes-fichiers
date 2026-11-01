package com.damien.mesfichiers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.ThumbnailUtils
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/** Cache disque des fichiers récupérés sur un serveur FTP. */
object FileCache {
    private val counter = AtomicInteger()

    private fun dir(): File {
        val d = File(AppModel.app.cacheDir, "ftp")
        d.mkdirs()
        return d
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Fichier local correspondant à la référence (téléchargé depuis le serveur si besoin). */
    suspend fun get(ref: ImageRef): File {
        val sid = ref.serverId ?: return File(ref.path)
        val pool = AppModel.pool(sid) ?: throw IOException("Serveur introuvable")
        val d = dir()
        val target = File(d, sha1(ref.key))
        if (target.exists() && target.length() > 0L) {
            target.setLastModified(System.currentTimeMillis())
            return target
        }
        pool.use { c ->
            val tmp = File(d, target.name + "." + System.nanoTime() + ".part")
            try {
                tmp.outputStream().use { out -> FtpOps.download(c, ref.path, out) }
                if (!tmp.renameTo(target)) throw IOException("Écriture du cache impossible")
            } finally {
                tmp.delete()
            }
            Unit
        }
        if (counter.incrementAndGet() % 25 == 0) trim()
        return target
    }

    /** Copie le fichier sous son vrai nom (pour l'ouvrir ou le partager avec la bonne extension). */
    fun named(f: File, name: String): File {
        if (f.name == name) return f
        val d = File(AppModel.app.cacheDir, "open")
        d.mkdirs()
        val t = File(d, name)
        f.copyTo(t, overwrite = true)
        return t
    }

    fun trim() {
        val files = dir().listFiles() ?: return
        var total = 0L
        for (f in files) total += f.length()
        val limit = 600L * 1024L * 1024L
        if (total <= limit) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= limit * 8 / 10) break
            total -= f.length()
            f.delete()
        }
    }
}

object Images {
    private val mem = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024L / 8L).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val decodeSem = Semaphore(3)

    fun peek(ref: ImageRef, maxDim: Int): Bitmap? = mem.get(ref.key + "@" + maxDim)

    suspend fun load(ref: ImageRef, maxDim: Int): Bitmap? {
        val k = ref.key + "@" + maxDim
        val hit = mem.get(k)
        if (hit != null) return hit
        val bmp: Bitmap? = if (ref.video) {
            decodeSem.withPermit { withContext(Dispatchers.IO) { videoThumb(ref.path, maxDim) } }
        } else {
            val file = FileCache.get(ref)
            decodeSem.withPermit { withContext(Dispatchers.IO) { decode(file, maxDim) } }
        }
        if (bmp != null) mem.put(k, bmp)
        return bmp
    }

    private fun videoThumb(path: String, maxDim: Int): Bitmap? = try {
        ThumbnailUtils.createVideoThumbnail(File(path), Size(maxDim, maxDim), null)
    } catch (e: Exception) {
        null
    }

    private fun decode(file: File, maxDim: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val big = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (big / sample > maxDim) sample *= 2
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            val raw = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
            fixOrientation(raw, orientation(file))
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun orientation(file: File): Int = try {
        ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun fixOrientation(b: Bitmap, o: Int): Bitmap {
        val m = Matrix()
        when (o) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                m.postRotate(90f)
                m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                m.postRotate(270f)
                m.postScale(-1f, 1f)
            }
            else -> return b
        }
        return try {
            Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
        } catch (e: OutOfMemoryError) {
            b
        }
    }
}
