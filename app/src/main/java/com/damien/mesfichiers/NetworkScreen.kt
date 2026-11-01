@file:OptIn(ExperimentalFoundationApi::class)

package com.damien.mesfichiers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NetworkScreen() {
    val cs = MaterialTheme.colorScheme
    val selected = remember { mutableStateListOf<String>() }
    val selecting = selected.isNotEmpty()
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Server?>(null) }

    BackHandler(enabled = selecting) { selected.clear() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (selecting) {
                SelectionHeader(
                    count = selected.size,
                    allSelected = selected.size == AppModel.servers.size,
                    onToggleAll = {
                        if (selected.size == AppModel.servers.size) {
                            selected.clear()
                        } else {
                            selected.clear()
                            selected.addAll(AppModel.servers.map { it.id })
                        }
                    },
                    onCancel = { selected.clear() }
                )
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { AppModel.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { AppModel.push(Screen.Form(null)) }) {
                        Icon(Icons.Default.Add, contentDescription = "Ajouter un serveur")
                    }
                }
                Crumbs(listOf("Espace de stockage réseau" to { }))
            }

            if (AppModel.servers.isEmpty()) {
                Text(
                    "Aucun serveur pour l'instant.\nAppuie sur + pour ajouter ton serveur FTP.",
                    color = cs.onSurfaceVariant,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(32.dp)
                )
            }

            LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp)) {
                items(AppModel.servers, key = { it.id }) { s ->
                    val isSel = s.id in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 5.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (isSel) cs.primary.copy(alpha = 0.16f) else cs.surface)
                            .combinedClickable(
                                onClick = {
                                    if (selecting) {
                                        if (isSel) selected.remove(s.id) else selected.add(s.id)
                                    } else {
                                        AppModel.push(Screen.Browse(Loc.Ftp(s.id, "~")))
                                    }
                                },
                                onLongClick = { if (!isSel) selected.add(s.id) }
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selecting) {
                            SelCircle(isSel)
                            Spacer(Modifier.width(12.dp))
                        }
                        IconChip(Glyph.SERVER, androidx.compose.ui.graphics.Color(0xFF3B7BFF), 50.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.cfg.host, fontSize = 14.sp, color = cs.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }

        if (selecting) {
            val actions = ArrayList<BarAction>()
            if (selected.size == 1) {
                actions.add(BarAction("Détails", { Icon(Icons.Default.Info, contentDescription = null) }) {
                    val id = selected[0]
                    selected.clear()
                    AppModel.push(Screen.Form(id))
                })
                actions.add(BarAction("Renommer", { Icon(Icons.Default.Edit, contentDescription = null) }) {
                    renaming = AppModel.servers.firstOrNull { it.id == selected[0] }
                })
            }
            actions.add(BarAction("Supprimer", { Icon(Icons.Default.Delete, contentDescription = null) }) { confirmDelete = true })
            ActionBar(
                actions,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp)
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ?") },
            text = { Text("Les serveurs sélectionnés seront retirés de l'appli. Rien n'est supprimé sur les serveurs eux-mêmes.") },
            confirmButton = {
                TextButton(onClick = {
                    AppModel.deleteServers(selected.toList())
                    selected.clear()
                    confirmDelete = false
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } }
        )
    }

    val target = renaming
    if (target != null) {
        var text by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Renommer") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        AppModel.renameServer(target.id, text.trim())
                        renaming = null
                        selected.clear()
                    }
                ) { Text("Renommer") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Annuler") } }
        )
    }
}
