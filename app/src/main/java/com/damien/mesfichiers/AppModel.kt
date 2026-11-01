package com.damien.mesfichiers

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

data class Server(val id: String, val name: String, val cfg: FtpConfig)

sealed interface Screen {
    val uid: Long

    data object Home : Screen {
        override val uid: Long = 0L
    }

    data object Network : Screen {
        override val uid: Long = 1L
    }

    data class Browse(val loc: Loc, override val uid: Long = AppModel.nextUid()) : Screen
    data class Form(val serverId: String?, override val uid: Long = AppModel.nextUid()) : Screen
    data class Viewer(val refs: List<ImageRef>, val index: Int, override val uid: Long = AppModel.nextUid()) : Screen
}

object AppModel {
    private var inited = false
    lateinit var app: Context
        private set
    private lateinit var prefs: SharedPreferences

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val servers = mutableStateListOf<Server>()
    val stack = mutableStateListOf<Screen>(Screen.Home)
    var permTick by mutableIntStateOf(0)
    var showHidden by mutableStateOf(false)
        private set

    /** Dernier listing connu de chaque dossier : affiché tout de suite pendant le rechargement. */
    val cache = HashMap<Loc, List<Entry>>()

    /** Dossier de connexion résolu de chaque serveur (pour le fil d'Ariane). */
    val homes = HashMap<String, String>()

    private var uidGen = 10L
    fun nextUid(): Long = uidGen++

    @Volatile
    private var snapshot: List<Server> = emptyList()
    private val pools = HashMap<String, FtpPool>()

    fun init(ctx: Context) {
        if (inited) return
        inited = true
        app = ctx.applicationContext
        prefs = app.getSharedPreferences("mesfichiers", Context.MODE_PRIVATE)
        showHidden = prefs.getBoolean("hidden", false)
        loadServers()
        scope.launch(Dispatchers.IO) { FileCache.trim() }
    }

    fun updateShowHidden(v: Boolean) {
        showHidden = v
        prefs.edit().putBoolean("hidden", v).apply()
    }

    // ------------------------------------------------------------ navigation

    fun push(s: Screen) {
        stack.add(s)
    }

    fun back(): Boolean {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            return true
        }
        return false
    }

    fun toHome() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Va vers un dossier : revient à l'écran déjà dans la pile s'il existe, sinon l'empile. */
    fun goTo(loc: Loc) {
        val i = stack.indexOfLast { it is Screen.Browse && it.loc == loc }
        if (i >= 0) {
            while (stack.size > i + 1) stack.removeAt(stack.lastIndex)
        } else {
            stack.add(Screen.Browse(loc))
        }
    }

    // ------------------------------------------------------------ serveurs

    private fun loadServers() {
        val txt = prefs.getString("servers", null)
        if (txt != null) {
            try {
                val arr = JSONArray(txt)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val sec = try {
                        Security.valueOf(o.optString("sec", "NONE"))
                    } catch (e: Exception) {
                        Security.NONE
                    }
                    val cfg = FtpConfig(
                        host = o.optString("host"),
                        port = o.optInt("port", 21),
                        security = sec,
                        user = o.optString("user"),
                        password = Vault.decrypt(o.optString("pw")),
                        anonymous = o.optBoolean("anon", false),
                        passive = o.optBoolean("passive", true),
                        encoding = o.optString("enc", "UTF-8"),
                        trustAll = o.optBoolean("trust", false),
                        startPath = o.optString("start")
                    )
                    servers.add(Server(o.getString("id"), o.optString("name"), cfg))
                }
            } catch (e: Exception) {
                // préférences illisibles : on repart d'une liste vide
            }
        }
        snapshot = servers.toList()
    }

    private fun persist() {
        val arr = JSONArray()
        for (s in servers) {
            val o = JSONObject()
            o.put("id", s.id)
            o.put("name", s.name)
            o.put("host", s.cfg.host)
            o.put("port", s.cfg.port)
            o.put("sec", s.cfg.security.name)
            o.put("user", s.cfg.user)
            o.put("pw", Vault.encrypt(s.cfg.password))
            o.put("anon", s.cfg.anonymous)
            o.put("passive", s.cfg.passive)
            o.put("enc", s.cfg.encoding)
            o.put("trust", s.cfg.trustAll)
            o.put("start", s.cfg.startPath)
            arr.put(o)
        }
        prefs.edit().putString("servers", arr.toString()).apply()
        snapshot = servers.toList()
    }

    @Synchronized
    private fun dropPool(id: String) {
        pools.remove(id)?.close()
    }

    fun saveServer(s: Server) {
        val i = servers.indexOfFirst { it.id == s.id }
        if (i >= 0) servers[i] = s else servers.add(s)
        persist()
        dropPool(s.id)
        homes.remove(s.id)
        cache.keys.removeAll { it is Loc.Ftp && it.serverId == s.id }
    }

    fun renameServer(id: String, name: String) {
        val i = servers.indexOfFirst { it.id == id }
        if (i < 0) return
        servers[i] = servers[i].copy(name = name)
        persist()
    }

    fun setStartPath(id: String, path: String) {
        val i = servers.indexOfFirst { it.id == id }
        if (i < 0) return
        val s = servers[i]
        servers[i] = s.copy(cfg = s.cfg.copy(startPath = path))
        persist()
        dropPool(id)
        homes.remove(id)
        cache.keys.removeAll { it is Loc.Ftp && it.serverId == id }
    }

    fun deleteServers(ids: Collection<String>) {
        servers.removeAll { it.id in ids }
        persist()
        for (id in ids) {
            dropPool(id)
            homes.remove(id)
            cache.keys.removeAll { it is Loc.Ftp && it.serverId == id }
        }
    }

    @Synchronized
    fun pool(id: String): FtpPool? {
        val existing = pools[id]
        if (existing != null) return existing
        val s = snapshot.firstOrNull { it.id == id } ?: return null
        val p = FtpPool(s.cfg)
        pools[id] = p
        return p
    }

    fun friendly(e: Throwable): String = when (e) {
        is UnknownHostException -> "Serveur introuvable (" + (e.message ?: "") + ")"
        is SocketTimeoutException -> "Délai dépassé : le serveur ne répond pas"
        is ConnectException -> "Connexion refusée : vérifie l'adresse et le port"
        is SSLException -> "Erreur TLS : " + (e.message ?: "")
        is IOException -> e.message?.takeIf { it.isNotBlank() } ?: "Erreur réseau"
        else -> e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
    }
}

object Ops {
    suspend fun deleteLocal(paths: List<String>) {
        val failed = kotlinx.coroutines.withContext(Dispatchers.IO) {
            var fail = 0
            for (p in paths) {
                if (!File(p).deleteRecursively()) fail++
            }
            android.media.MediaScannerConnection.scanFile(AppModel.app, paths.toTypedArray(), null, null)
            fail
        }
        if (failed > 0) throw IOException("$failed élément(s) n'ont pas pu être supprimés")
    }

    suspend fun deleteFtp(serverId: String, entries: List<Entry>) {
        val pool = AppModel.pool(serverId) ?: throw IOException("Serveur introuvable")
        pool.use { c ->
            for (e in entries) FtpOps.deleteRecursive(c, e.path, e.isDir, e.isLink)
            Unit
        }
    }

    suspend fun renameLocal(path: String, newName: String) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val f = File(path)
            val t = File(f.parentFile, newName)
            if (t.exists()) throw IOException("« $newName » existe déjà")
            if (!f.renameTo(t)) throw IOException("Renommage impossible")
            android.media.MediaScannerConnection.scanFile(AppModel.app, arrayOf(path, t.absolutePath), null, null)
            Unit
        }
    }

    suspend fun renameFtp(serverId: String, path: String, newName: String) {
        val pool = AppModel.pool(serverId) ?: throw IOException("Serveur introuvable")
        pool.use { c ->
            FtpOps.rename(c, path, FtpOps.join(FtpOps.parentOf(path), newName))
            Unit
        }
    }
}
