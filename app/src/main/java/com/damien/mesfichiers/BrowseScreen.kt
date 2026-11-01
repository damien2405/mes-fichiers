@file:OptIn(ExperimentalFoundationApi::class)

package com.damien.mesfichiers

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private sealed interface Load {
    data object Loading : Load
    data class Done(val entries: List<Entry>) : Load
    data class Error(val msg: String) : Load
}

private val SORT_LABELS = listOf("Nom", "Date", "Taille", "Type")

@Composable
fun BrowseScreen(screen: Screen.Browse) {
    val needsPerm = screen.loc !is Loc.Ftp
    val hasPerm = remember(AppModel.permTick) { !needsPerm || Environment.isExternalStorageManager() }
    if (hasPerm) BrowseContent(screen.loc) else PermissionPrompt()
}

@Composable
private fun PermissionPrompt() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            IconButton(onClick = { AppModel.back() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
            }
        }
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Accès aux fichiers", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text(
                "Pour parcourir le stockage de ton téléphone, l'appli a besoin de l'accès à tous les fichiers. Active-le sur l'écran suivant, puis reviens ici.",
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                try {
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName))
                    )
                } catch (e: Exception) {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }) { Text("Ouvrir les réglages") }
        }
    }
}

@Composable
private fun BrowseContent(loc: Loc) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val isFtp = loc is Loc.Ftp
    val isCategory = loc is Loc.Category
    val serverId = if (loc is Loc.Ftp) loc.serverId else null

    var reload by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<Load>(AppModel.cache[loc]?.let { Load.Done(it) } ?: Load.Loading) }
    var sortKey by rememberSaveable { mutableStateOf(if (isCategory) 1 else 0) }
    var asc by rememberSaveable { mutableStateOf(!isCategory) }
    var query by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateListOf<String>() }
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Entry?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val hidden = AppModel.showHidden

    LaunchedEffect(loc, reload, hidden) {
        if (state !is Load.Done) state = Load.Loading
        state = try {
            val list: List<Entry> = when (loc) {
                is Loc.Local -> withContext(Dispatchers.IO) { listLocal(loc.path, hidden) }
                is Loc.Category -> withContext(Dispatchers.IO) { queryCategory(loc.cat) }
                is Loc.Ftp -> {
                    val pool = AppModel.pool(loc.serverId) ?: throw IOException("Serveur introuvable")
                    val res = pool.use { c ->
                        val dir = FtpOps.resolve(c, loc.path)
                        Pair(dir, FtpOps.list(c, dir))
                    }
                    if (loc.path == "~") AppModel.homes[loc.serverId] = res.first
                    res.second.map { f -> Entry(f.name, FtpOps.join(res.first, f.name), f.isDir, f.size, f.modified, f.isLink) }
                }
            }
            AppModel.cache[loc] = list
            Load.Done(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Error(AppModel.friendly(e))
        }
    }

    val all: List<Entry> = (state as? Load.Done)?.entries ?: emptyList()
    val q = query
    val shown = remember(all, sortKey, asc, q) {
        val sorted = sortEntries(all, sortKey, asc)
        if (q.isNullOrBlank()) sorted else sorted.filter { it.name.contains(q, ignoreCase = true) }
    }
    val selecting = selected.isNotEmpty()
    val searching = q != null

    BackHandler(enabled = selecting || searching) {
        if (selecting) selected.clear() else query = null
    }

    fun refOf(e: Entry): ImageRef =
        ImageRef(serverId, e.path, e.name, e.size, e.modified, video = kindOf(e.name) == FileKind.VIDEO)

    fun runOp(msg: String, block: suspend () -> Unit) {
        scope.launch {
            busy = msg
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(AppModel.friendly(e))
            } finally {
                busy = null
            }
        }
    }

    fun toggle(e: Entry) {
        if (e.path in selected) selected.remove(e.path) else selected.add(e.path)
    }

    fun openEntry(e: Entry) {
        if (selecting) {
            toggle(e)
            return
        }
        val kind = kindOf(e.name)
        if (e.isDir) {
            AppModel.goTo(if (loc is Loc.Ftp) Loc.Ftp(loc.serverId, e.path) else Loc.Local(e.path))
        } else if (kind == FileKind.IMAGE) {
            val imgs = shown.filter { kindOf(it.name) == FileKind.IMAGE }
            val idx = imgs.indexOfFirst { it.path == e.path }.coerceAtLeast(0)
            AppModel.push(Screen.Viewer(imgs.map { refOf(it) }, idx))
        } else if (isFtp) {
            runOp("Téléchargement de " + e.name + "…") {
                val f = FileCache.get(refOf(e))
                val named = withContext(Dispatchers.IO) { FileCache.named(f, e.name) }
                openFile(ctx, named, e.name)
            }
        } else {
            openFile(ctx, File(e.path), e.name)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (selecting) {
                SelectionHeader(
                    count = selected.size,
                    allSelected = selected.size == shown.size,
                    onToggleAll = {
                        if (selected.size == shown.size) {
                            selected.clear()
                        } else {
                            selected.clear()
                            selected.addAll(shown.map { it.path })
                        }
                    },
                    onCancel = { selected.clear() }
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (searching) query = null else AppModel.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                    if (q != null) {
                        TextField(
                            value = q,
                            onValueChange = { query = it },
                            placeholder = { Text("Rechercher") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { query = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer la recherche")
                        }
                    } else {
                        Text(
                            titleOf(loc),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Search, contentDescription = "Rechercher")
                        }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Actualiser") }, onClick = {
                                    menu = false
                                    reload++
                                })
                                if (!isFtp) {
                                    DropdownMenuItem(
                                        text = { Text(if (hidden) "Masquer les fichiers cachés" else "Afficher les fichiers cachés") },
                                        onClick = {
                                            menu = false
                                            AppModel.updateShowHidden(!hidden)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Crumbs(crumbsOf(loc))
            }

            // ligne de tri, comme dans « Mes fichiers »
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (shown.size > 1) shown.size.toString() + " éléments" else shown.size.toString() + " élément",
                    fontSize = 14.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    Text(
                        SORT_LABELS[sortKey] + " ▾",
                        fontSize = 15.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { sortMenu = true }.padding(8.dp)
                    )
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SORT_LABELS.forEachIndexed { i, l ->
                            DropdownMenuItem(text = { Text(l) }, onClick = {
                                sortKey = i
                                sortMenu = false
                            })
                        }
                    }
                }
                Text(
                    if (asc) "↑" else "↓",
                    fontSize = 18.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { asc = !asc }.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            val st = state
            when {
                st is Load.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                st is Load.Error -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(st.msg, color = cs.error, textAlign = TextAlign.Center, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { reload++ }) { Text("Réessayer") }
                }
                shown.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (q.isNullOrBlank()) "Aucun fichier" else "Aucun résultat",
                        color = cs.onSurfaceVariant,
                        fontSize = 16.sp
                    )
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = if (selecting) 110.dp else 16.dp)
                ) {
                    items(shown, key = { it.path }) { e ->
                        val isSel = e.path in selected
                        EntryRow(
                            e = e,
                            ref = if (e.isDir) null else refOf(e),
                            showParent = isCategory,
                            selecting = selecting,
                            selected = isSel,
                            onClick = { openEntry(e) },
                            onLong = { if (!isSel) selected.add(e.path) }
                        )
                    }
                    item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
                }
            }
        }

        if (selecting) {
            val chosen = shown.filter { it.path in selected }
            val actions = ArrayList<BarAction>()
            val hasFiles = chosen.any { !it.isDir }
            if (isFtp && hasFiles) {
                actions.add(BarAction("Télécharger", { GlyphIcon(Glyph.DOWNLOAD, cs.onSurface, Modifier.size(24.dp)) }) {
                    runOp("Téléchargement…") {
                        var n = 0
                        for (e in chosen) {
                            if (e.isDir) continue
                            val f = FileCache.get(refOf(e))
                            val ok = withContext(Dispatchers.IO) { saveToDownloads(ctx, f, e.name) }
                            if (ok) n++
                        }
                        selected.clear()
                        toast(n.toString() + " fichier(s) enregistré(s) dans Téléchargements")
                    }
                })
            }
            if (!isFtp && hasFiles) {
                actions.add(BarAction("Partager", { Icon(Icons.Default.Share, contentDescription = null) }) {
                    shareFiles(ctx, chosen.filter { !it.isDir }.map { File(it.path) })
                })
            }
            if (chosen.size == 1) {
                actions.add(BarAction("Renommer", { Icon(Icons.Default.Edit, contentDescription = null) }) {
                    renaming = chosen[0]
                })
            }
            actions.add(BarAction("Supprimer", { Icon(Icons.Default.Delete, contentDescription = null) }) {
                confirmDelete = true
            })
            ActionBar(
                actions,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp)
            )
        }
    }

    if (confirmDelete) {
        val chosen = shown.filter { it.path in selected }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ?") },
            text = {
                Text(
                    (if (chosen.size == 1) "« " + chosen[0].name + " »" else chosen.size.toString() + " éléments") +
                        (if (isFtp) " seront supprimés définitivement du serveur." else " seront supprimés définitivement.")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    runOp("Suppression…") {
                        if (serverId != null) Ops.deleteFtp(serverId, chosen) else Ops.deleteLocal(chosen.map { it.path })
                        selected.clear()
                        AppModel.cache.remove(loc)
                        reload++
                    }
                }) { Text("Supprimer", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } }
        )
    }

    val target = renaming
    if (target != null) {
        var text by remember(target.path) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Renommer") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank() && !text.contains('/'),
                    onClick = {
                        renaming = null
                        val newName = text.trim()
                        runOp("Renommage…") {
                            if (serverId != null) Ops.renameFtp(serverId, target.path, newName) else Ops.renameLocal(target.path, newName)
                            selected.clear()
                            AppModel.cache.remove(loc)
                            reload++
                        }
                    }
                ) { Text("Renommer") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Annuler") } }
        )
    }

    val b = busy
    if (b != null) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = { },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(b, fontSize = 16.sp)
                }
            }
        )
    }
}

private fun titleOf(loc: Loc): String = when (loc) {
    is Loc.Category -> loc.cat.label
    is Loc.Local -> {
        val root = Environment.getExternalStorageDirectory().absolutePath
        if (loc.path == root) "Stockage interne" else File(loc.path).name
    }
    is Loc.Ftp -> {
        val name = AppModel.servers.firstOrNull { it.id == loc.serverId }?.name ?: "Serveur"
        if (loc.path == "/" || loc.path == "~") name else loc.path.trimEnd('/').substringAfterLast('/')
    }
}

private fun crumbsOf(loc: Loc): List<Pair<String, () -> Unit>> {
    val out = ArrayList<Pair<String, () -> Unit>>()
    when (loc) {
        is Loc.Category -> out.add(loc.cat.label to { })
        is Loc.Local -> {
            val root = Environment.getExternalStorageDirectory().absolutePath
            out.add("Stockage interne" to { AppModel.goTo(Loc.Local(root)) })
            if (loc.path != root && loc.path.startsWith(root)) {
                var cur = root
                for (seg in loc.path.removePrefix(root).split('/')) {
                    if (seg.isEmpty()) continue
                    cur = "$cur/$seg"
                    val p = cur
                    out.add(seg to { AppModel.goTo(Loc.Local(p)) })
                }
            }
        }
        is Loc.Ftp -> {
            val name = AppModel.servers.firstOrNull { it.id == loc.serverId }?.name ?: "Serveur"
            out.add(name to { AppModel.goTo(Loc.Ftp(loc.serverId, "~")) })
            if (loc.path != "~") {
                val home = AppModel.homes[loc.serverId]?.trimEnd('/')
                var cur = ""
                var rel = loc.path
                if (!home.isNullOrEmpty() && loc.path.startsWith("$home/")) {
                    cur = home
                    rel = loc.path.removePrefix(home)
                }
                for (seg in rel.split('/')) {
                    if (seg.isEmpty()) continue
                    cur = "$cur/$seg"
                    val p = cur
                    out.add(seg to { AppModel.goTo(Loc.Ftp(loc.serverId, p)) })
                }
            }
        }
    }
    return out
}

@Composable
private fun EntryRow(
    e: Entry,
    ref: ImageRef?,
    showParent: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLong: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val sub = StringBuilder()
    if (showParent && e.parentName != null) sub.append(e.parentName).append("  ·  ")
    if (e.isDir && e.childCount >= 0) {
        sub.append(if (e.childCount > 1) e.childCount.toString() + " éléments" else e.childCount.toString() + " élément")
        val d = fmtDate(e.modified)
        if (d.isNotEmpty()) sub.append("  ·  ").append(d)
    } else {
        sub.append(fmtDate(e.modified))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) cs.primary.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) {
            SelCircle(selected)
            Spacer(Modifier.width(12.dp))
        }
        FileIcon(e, ref, 54.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) {
                Text(sub.toString(), fontSize = 13.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!e.isDir && e.size > 0L) {
            Spacer(Modifier.width(8.dp))
            Text(fmtSize(e.size), fontSize = 13.sp, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun FileIcon(e: Entry, ref: ImageRef?, size: Dp) {
    if (e.isDir) {
        GlyphIcon(Glyph.FOLDER, FolderColor, Modifier.size(size))
        return
    }
    val kind = kindOf(e.name)
    val color = kindColor(kind)
    if (ref != null && (kind == FileKind.IMAGE || (kind == FileKind.VIDEO && ref.serverId == null))) {
        Thumb(ref, kind, size)
        return
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        when (kind) {
            FileKind.AUDIO -> GlyphIcon(Glyph.AUDIO, color, Modifier.size(30.dp))
            FileKind.VIDEO -> GlyphIcon(Glyph.VIDEO, color, Modifier.size(30.dp))
            FileKind.IMAGE -> GlyphIcon(Glyph.IMAGE, color, Modifier.size(30.dp))
            FileKind.APK -> GlyphIcon(Glyph.APK, color, Modifier.size(30.dp))
            else -> {
                val ext = extOf(e.name).uppercase()
                Text(
                    if (ext.isEmpty()) "?" else ext.take(4),
                    color = color,
                    fontSize = if (ext.length > 3) 11.sp else 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun Thumb(ref: ImageRef, kind: FileKind, size: Dp) {
    val color = kindColor(kind)
    var bmp by remember(ref.key) { mutableStateOf(Images.peek(ref, 384)) }
    LaunchedEffect(ref.key) {
        if (bmp == null) {
            bmp = try {
                Images.load(ref, 384)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            GlyphIcon(if (kind == FileKind.VIDEO) Glyph.VIDEO else Glyph.IMAGE, color, Modifier.size(28.dp))
        }
    }
}
