package com.damien.mesfichiers

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val VIEW_DIM = 3072

@Composable
fun ViewerScreen(s: Screen.Viewer) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = s.index.coerceIn(0, s.refs.lastIndex), pageCount = { s.refs.size })
    var chrome by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val current = s.refs[pager.currentPage.coerceIn(0, s.refs.lastIndex)]

    // précharge l'image suivante et la précédente sur le disque pour que le défilement soit fluide
    LaunchedEffect(pager.currentPage) {
        for (d in intArrayOf(1, -1)) {
            val r = s.refs.getOrNull(pager.currentPage + d) ?: continue
            try {
                FileCache.get(r)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // sans importance : l'image sera chargée à l'affichage
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { s.refs[it].key }
        ) { page ->
            ImagePage(s.refs[page]) { chrome = !chrome }
        }

        if (chrome) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0x99000000))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { AppModel.back() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(current.name, color = Color.White, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (pager.currentPage + 1).toString() + " / " + s.refs.size + "  ·  " + fmtSize(current.size),
                        color = Color(0xCCFFFFFF),
                        fontSize = 13.sp
                    )
                }
                if (busy) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                }
                if (current.serverId != null) {
                    IconButton(onClick = {
                        if (busy) return@IconButton
                        scope.launch {
                            busy = true
                            try {
                                val f = FileCache.get(current)
                                val ok = withContext(Dispatchers.IO) { saveToDownloads(ctx, f, current.name) }
                                toast(if (ok) "Enregistré dans Téléchargements" else "Enregistrement impossible")
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                toast(AppModel.friendly(e))
                            } finally {
                                busy = false
                            }
                        }
                    }) {
                        GlyphIcon(Glyph.DOWNLOAD, Color.White, Modifier.size(24.dp))
                    }
                }
                IconButton(onClick = {
                    if (busy) return@IconButton
                    scope.launch {
                        busy = true
                        try {
                            val f = FileCache.get(current)
                            val named = withContext(Dispatchers.IO) { FileCache.named(f, current.name) }
                            shareFiles(ctx, listOf(named))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            toast(AppModel.friendly(e))
                        } finally {
                            busy = false
                        }
                    }
                }) {
                    Icon(Icons.Default.Share, contentDescription = "Partager", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ImagePage(ref: ImageRef, onTap: () -> Unit) {
    var bmp by remember(ref.key) { mutableStateOf<Bitmap?>(Images.peek(ref, VIEW_DIM)) }
    val thumb = remember(ref.key) { Images.peek(ref, 384) }
    var error by remember(ref.key) { mutableStateOf<String?>(null) }
    var scale by remember(ref.key) { mutableFloatStateOf(1f) }
    var offset by remember(ref.key) { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(ref.key) {
        if (bmp == null) {
            try {
                bmp = Images.load(ref, VIEW_DIM)
                if (bmp == null) error = "Image illisible"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = AppModel.friendly(e)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { box = it }
            .pointerInput(ref.key) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
            .pointerInput(ref.key) {
                // pincement pour zoomer ; déplacement du doigt seulement une fois zoomé,
                // sinon le geste reste au pager pour changer d'image
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (zoom != 1f || scale > 1f) {
                            val ns = (scale * zoom).coerceIn(1f, 6f)
                            val maxX = box.width * (ns - 1f) / 2f
                            val maxY = box.height * (ns - 1f) / 2f
                            val moved = offset + pan
                            scale = ns
                            offset = if (ns <= 1f) Offset.Zero else Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val shown = bmp ?: thumb
        if (shown != null) {
            Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
        }
        val err = error
        if (bmp == null && err == null) {
            CircularProgressIndicator(color = Color.White)
        }
        if (err != null) {
            Text(err, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
        }
    }
}
