package com.damien.mesfichiers

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private data class Tile(val label: String, val glyph: Glyph, val color: Color, val onClick: () -> Unit)

@Composable
fun HomeScreen() {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val root = remember { Environment.getExternalStorageDirectory().absolutePath }

    val tiles = listOf(
        Tile("Téléchargements", Glyph.DOWNLOAD, Color(0xFF1FB59A)) {
            AppModel.push(Screen.Browse(Loc.Local("$root/Download")))
        },
        Tile("Docs", Glyph.DOC, Color(0xFFE0A03E)) { AppModel.push(Screen.Browse(Loc.Category(Cat.DOCS))) },
        Tile("Images", Glyph.IMAGE, Color(0xFFE5547E)) { AppModel.push(Screen.Browse(Loc.Category(Cat.IMAGES))) },
        Tile("Audio", Glyph.AUDIO, Color(0xFF6B73E8)) { AppModel.push(Screen.Browse(Loc.Category(Cat.AUDIO))) },
        Tile("Vidéos", Glyph.VIDEO, Color(0xFF8E4FE0)) { AppModel.push(Screen.Browse(Loc.Category(Cat.VIDEOS))) },
        Tile("Fichiers d'installation", Glyph.APK, Color(0xFF7BB500)) { AppModel.push(Screen.Browse(Loc.Category(Cat.APK))) }
    )

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 28.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Mes fichiers", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (AppModel.showHidden) "Masquer les fichiers cachés" else "Afficher les fichiers cachés") },
                        onClick = {
                            AppModel.updateShowHidden(!AppModel.showHidden)
                            menu = false
                        }
                    )
                }
            }
        }

        SectionCard {
            for (row in tiles.chunked(2)) {
                Row(Modifier.fillMaxWidth()) {
                    for (t in row) CategoryTile(t, Modifier.weight(1f))
                }
            }
        }

        Text(
            "Stockage",
            fontSize = 14.sp,
            color = cs.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 32.dp, top = 24.dp, bottom = 8.dp)
        )

        SectionCard {
            InternalStorageRow(root)
            for (s in AppModel.servers) {
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = cs.outlineVariant)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { AppModel.push(Screen.Browse(Loc.Ftp(s.id, "/"))) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlyphIcon(Glyph.SERVER, cs.onSurface, Modifier.size(34.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("FTP · " + s.cfg.host, fontSize = 14.sp, color = cs.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = cs.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { AppModel.push(Screen.Network) }
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlyphIcon(Glyph.SERVER, cs.onSurface, Modifier.size(34.dp))
                Spacer(Modifier.width(16.dp))
                Text("Espace de stockage réseau", fontSize = 18.sp)
            }
        }

        Spacer(Modifier.height(32.dp).navigationBarsPadding())
    }
}

@Composable
private fun CategoryTile(t: Tile, modifier: Modifier) {
    Row(
        modifier
            .clickable(onClick = t.onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(54.dp).clip(CircleShape).background(t.color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(t.glyph, t.color, Modifier.size(28.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(t.label, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun InternalStorageRow(root: String) {
    val cs = MaterialTheme.colorScheme
    val stat = remember { StatFs(root) }
    val total = stat.totalBytes.toDouble()
    val avail = stat.availableBytes.toDouble()
    // « 128 Go » : capacité commerciale (puissance de 2 au-dessus de la partition de données)
    var cap = 16.0
    while (cap * 1e9 < total) cap *= 2.0
    val capBytes = cap * 1e9
    val used = (capBytes - avail).coerceIn(0.0, capBytes)
    val frac = (used / capBytes).toFloat().coerceIn(0f, 1f)
    val pct = (frac * 100f).toInt()

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { AppModel.push(Screen.Browse(Loc.Local(root))) }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphIcon(Glyph.PHONE, cs.onSurface, Modifier.size(34.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Stockage interne", fontSize = 18.sp)
            Text("$pct% utilisé(s)", fontSize = 14.sp, color = cs.primary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                String.format(Locale.FRANCE, "%.1f Go / %d Go", used / 1e9, cap.toInt()),
                fontSize = 13.sp,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.width(120.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(cs.surfaceVariant)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width((120f * frac).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF7FE0C0), Color(0xFF5B8CFF))))
                )
            }
        }
    }
}
