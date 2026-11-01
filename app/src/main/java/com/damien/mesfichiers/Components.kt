@file:OptIn(ExperimentalFoundationApi::class)

package com.damien.mesfichiers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Glyph { FOLDER, IMAGE, VIDEO, AUDIO, DOC, DOWNLOAD, SERVER, PHONE, APK }

/** Icônes dessinées à la main (évite la grosse bibliothèque d'icônes étendues). */
@Composable
fun GlyphIcon(kind: Glyph, tint: Color, modifier: Modifier = Modifier) {
    if (kind == Glyph.APK) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("APK", color = tint, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
        return
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.075f
        val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            Glyph.FOLDER -> {
                val p = Path()
                p.moveTo(w * 0.08f, h * 0.22f)
                p.lineTo(w * 0.38f, h * 0.22f)
                p.lineTo(w * 0.48f, h * 0.34f)
                p.lineTo(w * 0.92f, h * 0.34f)
                p.lineTo(w * 0.92f, h * 0.80f)
                p.lineTo(w * 0.08f, h * 0.80f)
                p.close()
                drawPath(p, tint)
                drawPath(p, tint, style = Stroke(width = w * 0.06f, join = StrokeJoin.Round))
            }
            Glyph.IMAGE -> {
                drawRoundRect(
                    color = tint, topLeft = Offset(w * 0.15f, h * 0.2f), size = Size(w * 0.7f, h * 0.6f),
                    cornerRadius = CornerRadius(w * 0.1f), style = st
                )
                drawCircle(tint, radius = w * 0.065f, center = Offset(w * 0.36f, h * 0.38f))
                val p = Path()
                p.moveTo(w * 0.18f, h * 0.72f)
                p.lineTo(w * 0.42f, h * 0.5f)
                p.lineTo(w * 0.58f, h * 0.64f)
                p.lineTo(w * 0.7f, h * 0.54f)
                p.lineTo(w * 0.82f, h * 0.7f)
                drawPath(p, tint, style = st)
            }
            Glyph.VIDEO -> {
                drawRoundRect(
                    color = tint, topLeft = Offset(w * 0.15f, h * 0.2f), size = Size(w * 0.7f, h * 0.6f),
                    cornerRadius = CornerRadius(w * 0.1f), style = st
                )
                val p = Path()
                p.moveTo(w * 0.43f, h * 0.36f)
                p.lineTo(w * 0.43f, h * 0.64f)
                p.lineTo(w * 0.67f, h * 0.5f)
                p.close()
                drawPath(p, tint)
            }
            Glyph.AUDIO -> {
                drawCircle(tint, radius = w * 0.12f, center = Offset(w * 0.38f, h * 0.72f))
                drawLine(tint, Offset(w * 0.49f, h * 0.7f), Offset(w * 0.49f, h * 0.22f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.49f, h * 0.22f), Offset(w * 0.74f, h * 0.36f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            Glyph.DOC -> {
                val p = Path()
                p.moveTo(w * 0.27f, h * 0.15f)
                p.lineTo(w * 0.58f, h * 0.15f)
                p.lineTo(w * 0.76f, h * 0.33f)
                p.lineTo(w * 0.76f, h * 0.85f)
                p.lineTo(w * 0.27f, h * 0.85f)
                p.close()
                drawPath(p, tint, style = st)
                drawLine(tint, Offset(w * 0.38f, h * 0.55f), Offset(w * 0.65f, h * 0.55f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.38f, h * 0.7f), Offset(w * 0.6f, h * 0.7f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            Glyph.DOWNLOAD -> {
                drawLine(tint, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.5f, h * 0.62f), strokeWidth = sw, cap = StrokeCap.Round)
                val p = Path()
                p.moveTo(w * 0.3f, h * 0.46f)
                p.lineTo(w * 0.5f, h * 0.66f)
                p.lineTo(w * 0.7f, h * 0.46f)
                drawPath(p, tint, style = st)
                drawLine(tint, Offset(w * 0.22f, h * 0.82f), Offset(w * 0.78f, h * 0.82f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            Glyph.SERVER -> {
                drawRoundRect(
                    color = tint, topLeft = Offset(w * 0.12f, h * 0.15f), size = Size(w * 0.76f, h * 0.32f),
                    cornerRadius = CornerRadius(w * 0.1f), style = st
                )
                drawRoundRect(
                    color = tint, topLeft = Offset(w * 0.12f, h * 0.53f), size = Size(w * 0.76f, h * 0.32f),
                    cornerRadius = CornerRadius(w * 0.1f), style = st
                )
                drawCircle(tint, radius = w * 0.04f, center = Offset(w * 0.27f, h * 0.31f))
                drawCircle(tint, radius = w * 0.04f, center = Offset(w * 0.27f, h * 0.69f))
            }
            Glyph.PHONE -> {
                drawRoundRect(
                    color = tint, topLeft = Offset(w * 0.28f, h * 0.1f), size = Size(w * 0.44f, h * 0.8f),
                    cornerRadius = CornerRadius(w * 0.09f), style = st
                )
                drawLine(tint, Offset(w * 0.44f, h * 0.2f), Offset(w * 0.56f, h * 0.2f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            Glyph.APK -> {}
        }
    }
}

fun kindColor(k: FileKind): Color = when (k) {
    FileKind.IMAGE -> Color(0xFF2E9E6B)
    FileKind.VIDEO -> Color(0xFF8E5BE0)
    FileKind.AUDIO -> Color(0xFF5B6EE0)
    FileKind.PDF -> Color(0xFFE0524F)
    FileKind.DOC -> Color(0xFF3E7BE0)
    FileKind.SHEET -> Color(0xFF2E9E4F)
    FileKind.SLIDES -> Color(0xFFE0762E)
    FileKind.TEXT -> Color(0xFF7A7A80)
    FileKind.ARCHIVE -> Color(0xFFE08A1E)
    FileKind.APK -> Color(0xFF7BB500)
    FileKind.OTHER -> Color(0xFF7A7A80)
}

val FolderColor = Color(0xFFF5A623)

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(content = content)
    }
}

@Composable
fun SelCircle(selected: Boolean) {
    val cs = MaterialTheme.colorScheme
    val base = Modifier.size(26.dp).clip(CircleShape)
    Box(
        if (selected) base.background(cs.primary) else base.border(2.dp, cs.onSurfaceVariant.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = cs.onPrimary, modifier = Modifier.size(18.dp))
    }
}

/** Fil d'Ariane arrondi, comme dans « Mes fichiers ». */
@Composable
fun Crumbs(items: List<Pair<String, () -> Unit>>) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(cs.secondaryContainer)
            .horizontalScroll(rememberScrollState(), reverseScrolling = true)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { AppModel.toHome() }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Home, contentDescription = "Accueil", tint = cs.onSurfaceVariant)
        }
        items.forEachIndexed { i, item ->
            Text("›", color = cs.onSurfaceVariant, fontSize = 18.sp)
            val last = i == items.lastIndex
            Text(
                item.first,
                color = if (last) cs.primary else cs.onSurfaceVariant,
                fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = item.second)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }
    }
}

/** En-tête affiché pendant une sélection : « Tout », compteur et « Annuler ». */
@Composable
fun SelectionHeader(count: Int, allSelected: Boolean, onToggleAll: () -> Unit, onCancel: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onToggleAll).padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SelCircle(allSelected)
            Text("Tout", fontSize = 13.sp, color = cs.onSurface)
        }
        Spacer(Modifier.width(14.dp))
        Text("$count sélectionné" + if (count > 1) "s" else "", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        TextButton(onClick = onCancel) { Text("Annuler", fontSize = 17.sp) }
    }
}

data class BarAction(val label: String, val icon: @Composable () -> Unit, val onClick: () -> Unit)

/** Barre d'actions flottante en bas de l'écran pendant une sélection. */
@Composable
fun ActionBar(actions: List<BarAction>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(34.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (a in actions) {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(onClick = a.onClick)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    a.icon()
                    Spacer(Modifier.height(2.dp))
                    Text(a.label, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}
