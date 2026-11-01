package com.damien.mesfichiers

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
        Tile("Images", Glyph.IMAGE, Color(0xFFE5547E)) { AppModel.push(Screen.Browse(Loc.Category(Cat.IMAGES))) },
        Tile("Vidéos", Glyph.VIDEO, Color(0xFF8E4FE0)) { AppModel.push(Screen.Browse(Loc.Category(Cat.VIDEOS))) },
        Tile("Audio", Glyph.AUDIO, Color(0xFF5B6EE8)) { AppModel.push(Screen.Browse(Loc.Category(Cat.AUDIO))) },
        Tile("Docs", Glyph.DOC, Color(0xFFF0A020)) { AppModel.push(Screen.Browse(Loc.Category(Cat.DOCS))) },
        Tile("Fichiers d'installation", Glyph.APK, Color(0xFF7BB500)) { AppModel.push(Screen.Browse(Loc.Category(Cat.APK))) }
    )

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 28.dp, end = 8.dp, top = 18.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Mes fichiers", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("Ton téléphone et ton serveur, au même endroit", fontSize = 14.sp, color = cs.onSurfaceVariant)
            }
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

        StorageHero(root)

        SectionLabel("Catégories")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (row in tiles.chunked(3)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (t in row) CategoryCard(t, Modifier.weight(1f))
                }
            }
        }

        SectionLabel("Serveurs")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (s in AppModel.servers) {
                NavCard(
                    glyph = Glyph.SERVER,
                    color = Color(0xFF3B7BFF),
                    title = s.name,
                    subtitle = "FTP · " + s.cfg.host,
                    onClick = { AppModel.push(Screen.Browse(Loc.Ftp(s.id, "~"))) }
                )
            }
            NavCard(
                glyph = Glyph.SERVER,
                color = Color(0xFF7A8090),
                title = "Espace de stockage réseau",
                subtitle = if (AppModel.servers.isEmpty()) "Ajouter ton serveur FTP" else "Ajouter ou modifier un serveur",
                onClick = { AppModel.push(Screen.Network) }
            )
        }

        Spacer(Modifier.height(32.dp).navigationBarsPadding())
    }
}

@Composable
private fun StorageHero(root: String) {
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
    val track = cs.surfaceVariant
    val accent = cs.primary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable { AppModel.push(Screen.Browse(Loc.Local(root))) },
        shape = RoundedCornerShape(28.dp),
        color = cs.surface
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(96.dp)) {
                    val sw = 12.dp.toPx()
                    val arcSize = Size(size.width - sw, size.height - sw)
                    val topLeft = Offset(sw / 2f, sw / 2f)
                    drawArc(
                        color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        topLeft = topLeft, size = arcSize, style = Stroke(width = sw, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = accent, startAngle = -90f, sweepAngle = 360f * frac, useCenter = false,
                        topLeft = topLeft, size = arcSize, style = Stroke(width = sw, cap = StrokeCap.Round)
                    )
                }
                Text("$pct%", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text("Stockage interne", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    String.format(Locale.FRANCE, "%.1f Go utilisés sur %d Go", used / 1e9, cap.toInt()),
                    fontSize = 14.sp,
                    color = cs.onSurfaceVariant
                )
                Text(
                    String.format(Locale.FRANCE, "%.1f Go libres", avail / 1e9),
                    fontSize = 14.sp,
                    color = cs.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(t: Tile, modifier: Modifier) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = t.onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconChip(t.glyph, t.color, 48.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                t.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                minLines = 2
            )
        }
    }
}

@Composable
private fun NavCard(glyph: Glyph, color: Color, title: String, subtitle: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = cs.surface
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconChip(glyph, color, 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 13.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("›", fontSize = 26.sp, color = cs.onSurfaceVariant)
        }
    }
}
