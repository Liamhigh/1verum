package com.verumomnis.forensic.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.VoBackground
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoSurface
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Full-bleed constitution reader (verumglobal.foundation style).
 *
 * Pages of assets/constitution.pdf are rendered as full-width bitmaps with
 * pinch-to-zoom and double-tap zoom (pan while zoomed, state resets on page
 * change). The shared VerumTopBar supplies the slim top bar (logo + title);
 * this screen is just the reader canvas plus the bottom page bar.
 */
@Composable
fun ConstitutionScreen() {
    val context = LocalContext.current
    var pageCount by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    // The governing text is the repo's CONSTITUTION.md, copied into assets by the
    // syncConstitution Gradle task, so what a user reads here is what the
    // repository says. The sealed PDF remains available as the signed artifact,
    // but it is a separate sealing that has drifted from the text before, so it
    // is not the default view.
    var showSealedPdf by remember { mutableStateOf(false) }
    var blocks by remember { mutableStateOf<List<ConstitutionBlock>>(emptyList()) }

    LaunchedEffect(Unit) {
        blocks = withContext(Dispatchers.IO) {
            runCatching {
                ConstitutionMarkdown.parse(
                    context.assets.open("constitution.md").bufferedReader().use { it.readText() }
                )
            }.getOrDefault(emptyList())
        }
    }

    LaunchedEffect(page, showSealedPdf) {
        if (!showSealedPdf) return@LaunchedEffect
        val (bmp, count) = renderConstitutionPage(context, page)
        if (count > 0) pageCount = count
        if (bmp != null) bitmap = bmp
    }

    Column(modifier = Modifier.fillMaxSize().background(VoBackground)) {
        ReaderModeBar(
            showSealedPdf = showSealedPdf,
            onSelect = { showSealedPdf = it }
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            if (showSealedPdf) {
                val bmp = bitmap
                if (bmp == null) CircularProgressIndicator(color = VoGold)
                else ZoomablePage(bitmap = bmp, page = page)
            } else {
                if (blocks.isEmpty()) CircularProgressIndicator(color = VoGold)
                else ConstitutionText(blocks)
            }
        }
        if (showSealedPdf && pageCount > 0) {
            PageBar(
                page = page,
                pageCount = pageCount,
                onPrev = { if (page > 0) page -= 1 },
                onNext = { if (page < pageCount - 1) page += 1 }
            )
        }
    }
}

/** Switches between the governing text and the sealed PDF artifact. */
@Composable
private fun ReaderModeBar(showSealedPdf: Boolean, onSelect: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeChip("TEXT", selected = !showSealedPdf) { onSelect(false) }
        ModeChip("SEALED PDF", selected = showSealedPdf) { onSelect(true) }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) VoBackground else VoTextMuted,
        fontFamily = JetBrainsMono,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) VoGold else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/** Renders the parsed Constitution as scrollable, selectable text. */
@Composable
private fun ConstitutionText(blocks: List<ConstitutionBlock>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(blocks) { block ->
            when (block) {
                is ConstitutionBlock.Heading -> Text(
                    ConstitutionMarkdown.stripInline(block.text),
                    color = if (block.level <= 2) VoGold else VoTextPrimary,
                    fontSize = when (block.level) {
                        1 -> 22.sp; 2 -> 18.sp; 3 -> 15.sp; else -> 13.sp
                    },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = if (block.level <= 2) 14.dp else 8.dp)
                )
                is ConstitutionBlock.Paragraph -> Text(
                    ConstitutionMarkdown.stripInline(block.text),
                    color = VoTextPrimary, fontSize = 13.sp, lineHeight = 20.sp
                )
                is ConstitutionBlock.ListItem -> Row {
                    Text(
                        block.marker, color = VoGold, fontSize = 13.sp,
                        modifier = Modifier.width(26.dp)
                    )
                    Text(
                        ConstitutionMarkdown.stripInline(block.text),
                        color = VoTextPrimary, fontSize = 13.sp, lineHeight = 20.sp
                    )
                }
                is ConstitutionBlock.Quote -> Text(
                    ConstitutionMarkdown.stripInline(block.text),
                    color = VoTextMuted, fontSize = 12.sp, lineHeight = 19.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VoSurface, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                )
                is ConstitutionBlock.Code -> Text(
                    block.text,
                    color = VoTextMuted, fontFamily = JetBrainsMono, fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VoSurface, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                )
                is ConstitutionBlock.Table -> ConstitutionTable(block)
                ConstitutionBlock.Rule -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(VoGold.copy(alpha = 0.25f))
                )
            }
        }
    }
}

/**
 * Tables are rendered as stacked label/value pairs rather than columns: a
 * two-to-four column table with prose in every cell is unreadable at phone
 * width, and truncating a clause to make it fit is not an option here.
 */
@Composable
private fun ConstitutionTable(table: ConstitutionBlock.Table) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoSurface, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        table.rows.forEach { row ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEachIndexed { index, cell ->
                    if (cell.isBlank()) return@forEachIndexed
                    val label = table.header.getOrElse(index) { "" }
                    if (index == 0) {
                        Text(
                            ConstitutionMarkdown.stripInline(cell),
                            color = VoGold, fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            buildString {
                                if (label.isNotBlank()) append("$label: ")
                                append(ConstitutionMarkdown.stripInline(cell))
                            },
                            color = VoTextPrimary, fontSize = 12.sp, lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Page bitmap: vertical scroll at 1x (so the bottom of an A4 page is always
 * reachable), pinch-to-zoom 1x-4x with bounded pan while zoomed, and
 * double-tap zoom towards the tap point. Zoom/pan state resets on page change.
 */
@Composable
private fun ZoomablePage(bitmap: Bitmap, page: Int) {
    var scale by remember(page) { mutableStateOf(1f) }
    var offset by remember(page) { mutableStateOf(Offset.Zero) }
    val scrollState = remember(page) { ScrollState(0) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState, enabled = scale <= 1f)
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Constitution page ${page + 1}",
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(page) {
                    awaitEachGesture {
                        awaitFirstDown()
                        do {
                            val event = awaitPointerEvent()
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            // Consume only zoom gestures, or pans while zoomed in; at 1x
                            // un-consumed drags fall through to the vertical scroller.
                            if (zoom != 1f || scale > 1f) {
                                val newScale = (scale * zoom).coerceIn(1f, 4f)
                                scale = newScale
                                offset = if (newScale <= 1f) {
                                    Offset.Zero
                                } else {
                                    if (scrollState.value > 0) scope.launch { scrollState.scrollTo(0) }
                                    constrainPan(offset + pan, newScale, size)
                                }
                                event.changes.forEach { if (it.position != it.previousPosition) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(page) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2f
                                offset = constrainPan(
                                    Offset(size.width / 2f - tap.x, size.height / 2f - tap.y),
                                    2f,
                                    size
                                )
                            }
                        }
                    )
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

/** Bounds pan so the zoomed page can never be dragged fully off-screen. */
private fun constrainPan(offset: Offset, scale: Float, size: IntSize): Offset {
    val maxX = size.width * (scale - 1f) / 2f
    val maxY = size.height * (scale - 1f) / 2f + size.height * 0.1f
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

/** Bottom bar: "‹ PREVIOUS" | "PAGE N / 13" | "NEXT ›" — 48dp, horizontal. */
@Composable
private fun PageBar(page: Int, pageCount: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(VoSurface),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = onPrev, enabled = page > 0) {
            Text(
                "‹ PREVIOUS",
                fontFamily = JetBrainsMono,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                color = if (page > 0) VoGold else VoTextMuted
            )
        }
        Text(
            "PAGE ${page + 1} / $pageCount",
            fontFamily = JetBrainsMono,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = VoTextPrimary
        )
        TextButton(onClick = onNext, enabled = pageCount > 0 && page < pageCount - 1) {
            Text(
                "NEXT ›",
                fontFamily = JetBrainsMono,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                color = if (pageCount > 0 && page < pageCount - 1) VoGold else VoTextMuted
            )
        }
    }
}

/**
 * Renders one page of assets/constitution.pdf as a full-width bitmap
 * (width = screen width at 2x density for sharp pinch-zoom, height
 * proportional). Returns the bitmap and the total page count.
 */
private suspend fun renderConstitutionPage(context: Context, pageIndex: Int): Pair<Bitmap?, Int> =
    withContext(Dispatchers.IO) {
        runCatching {
            val file = File(context.cacheDir, "constitution_reader.pdf")
            if (!file.exists()) {
                context.assets.open("constitution.pdf").use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
            }
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            try {
                val count = renderer.pageCount
                val index = pageIndex.coerceIn(0, count - 1)
                val pdfPage = renderer.openPage(index)
                try {
                    val targetWidth = (context.resources.displayMetrics.widthPixels * 2).coerceAtMost(2048)
                    val scaleFactor = targetWidth.toFloat() / pdfPage.width.toFloat()
                    val targetHeight = (pdfPage.height * scaleFactor).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    bitmap to count
                } finally {
                    pdfPage.close()
                }
            } finally {
                renderer.close()
                pfd.close()
            }
        }.getOrElse { null to 0 }
    }
