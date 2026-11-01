package com.damien.mesfichiers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import org.apache.commons.net.util.TrustManagerUtils
import java.io.IOException
import java.io.OutputStream

enum class Security(val label: String) {
    NONE("Aucune"),
    FTPS_EXPLICIT("FTPS (explicite)"),
    FTPS_IMPLICIT("FTPS (implicite)")
}

data class FtpConfig(
    val host: String,
    val port: Int = 21,
    val security: Security = Security.NONE,
    val user: String = "",
    val password: String = "",
    val anonymous: Boolean = false,
    val passive: Boolean = true,
    val encoding: String = "UTF-8",
    val trustAll: Boolean = false
)

data class FtpEntry(
    val name: String,
    val isDir: Boolean,
    val isLink: Boolean,
    val size: Long,
    val modified: Long
)

object FtpOps {

    /** Ouvre une connexion FTP/FTPS authentifiée. Bloquant : à appeler hors du thread principal. */
    fun open(cfg: FtpConfig): FTPClient {
        val client: FTPClient = when (cfg.security) {
            Security.NONE -> FTPClient()
            Security.FTPS_EXPLICIT -> FTPSClient(false)
            Security.FTPS_IMPLICIT -> FTPSClient(true)
        }
        if (client is FTPSClient && cfg.trustAll) {
            client.setTrustManager(TrustManagerUtils.getAcceptAllTrustManager())
        }
        client.setConnectTimeout(15_000)
        client.setDefaultTimeout(30_000)
        client.setControlEncoding(cfg.encoding)
        client.setAutodetectUTF8(true)
        try {
            client.connect(cfg.host.trim(), cfg.port)
            if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                throw IOException("Le serveur a refusé la connexion")
            }
            val ok = if (cfg.anonymous) client.login("anonymous", "anonymous@") else client.login(cfg.user, cfg.password)
            if (!ok) throw IOException("Identifiants refusés par le serveur")
            if (client is FTPSClient) {
                client.execPBSZ(0)
                client.execPROT("P")
            }
            if (cfg.passive) client.enterLocalPassiveMode() else client.enterLocalActiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
            client.setBufferSize(128 * 1024)
            client.setDataTimeout(30_000)
        } catch (t: Throwable) {
            quietClose(client)
            throw t
        }
        return client
    }

    fun quietClose(c: FTPClient?) {
        if (c == null) return
        try {
            if (c.isConnected) {
                try { c.logout() } catch (e: Exception) { /* ignoré */ }
                c.disconnect()
            }
        } catch (e: Exception) { /* ignoré */ }
    }

    fun list(c: FTPClient, path: String): List<FtpEntry> {
        var files: Array<FTPFile>? = null
        // MLSD donne des dates fiables ; si le serveur ne le gère pas, on retombe sur LIST
        val m: Array<FTPFile>? = c.mlistDir(path)
        if (m != null && FTPReply.isPositiveCompletion(c.replyCode)) files = m
        if (files == null) {
            files = c.listFiles(path)
            if (!FTPReply.isPositiveCompletion(c.replyCode)) {
                throw IOException("Dossier inaccessible : " + c.replyString.trim())
            }
        }
        val out = ArrayList<FtpEntry>()
        for (f in files!!) {
            if (f == null) continue
            val raw = f.name ?: continue
            if (raw.isEmpty() || raw == "." || raw == ".." || raw.startsWith("/")) continue
            val name = raw.substringAfterLast('/')
            if (name.isEmpty() || name == "." || name == "..") continue
            val link = f.isSymbolicLink
            // un lien sans extension est traité comme un dossier (cas fréquent des partages NAS)
            val dir = f.isDirectory || (link && !name.contains('.'))
            val ts = f.timestamp
            out.add(FtpEntry(name, dir, link, if (f.size > 0) f.size else 0L, ts?.timeInMillis ?: 0L))
        }
        return out
    }

    fun download(c: FTPClient, path: String, out: OutputStream) {
        if (!c.retrieveFile(path, out)) {
            throw IOException("Téléchargement impossible : " + c.replyString.trim())
        }
    }

    fun rename(c: FTPClient, from: String, to: String) {
        if (!c.rename(from, to)) throw IOException("Renommage impossible : " + c.replyString.trim())
    }

    fun deleteRecursive(c: FTPClient, path: String, isDir: Boolean, isLink: Boolean) {
        if (isLink || !isDir) {
            if (!c.deleteFile(path)) throw IOException("Suppression impossible : " + c.replyString.trim())
            return
        }
        for (e in list(c, path)) {
            deleteRecursive(c, join(path, e.name), e.isDir, e.isLink)
        }
        if (!c.removeDirectory(path)) throw IOException("Suppression impossible : " + c.replyString.trim())
    }

    fun join(dir: String, name: String): String = if (dir.endsWith("/")) dir + name else "$dir/$name"

    fun parentOf(path: String): String {
        val i = path.trimEnd('/').lastIndexOf('/')
        return if (i <= 0) "/" else path.substring(0, i)
    }
}

/**
 * Petit pool de connexions : un client FTP n'est pas thread-safe, et charger
 * des miniatures demande plusieurs transferts en parallèle.
 */
class FtpPool(private val cfg: FtpConfig, size: Int = 3) {
    private val sem = Semaphore(size)
    private val idle = ArrayDeque<FTPClient>()
    @Volatile private var closed = false

    suspend fun <T> use(block: (FTPClient) -> T): T = sem.withPermit {
        withContext(Dispatchers.IO) {
            var reused: FTPClient? = synchronized(idle) { idle.removeLastOrNull() }
            if (reused != null && !alive(reused)) {
                FtpOps.quietClose(reused)
                reused = null
            }
            val client = reused ?: FtpOps.open(cfg)
            try {
                val result = block(client)
                if (closed) FtpOps.quietClose(client) else synchronized(idle) { idle.addLast(client) }
                result
            } catch (t: Throwable) {
                FtpOps.quietClose(client)
                throw t
            }
        }
    }

    private fun alive(c: FTPClient): Boolean = try {
        c.sendNoOp()
    } catch (e: Exception) {
        false
    }

    fun close() {
        closed = true
        synchronized(idle) {
            for (c in idle) FtpOps.quietClose(c)
            idle.clear()
        }
    }
}
