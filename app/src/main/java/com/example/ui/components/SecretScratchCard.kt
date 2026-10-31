package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.game.engine.SpyGameEngine
import com.example.ui.theme.EmeraldConfirm
import com.example.ui.theme.NoirElevatedCard
import com.example.ui.theme.SpyCrimson
import com.example.ui.theme.SpyGold

/**
 * Authentic interactive scratch-off secret card (`كارت الخربشة`) built on Jetpack Compose Canvas
 * with `CompositingStrategy.Offscreen` + `BlendMode.Clear` and grid-cell coverage tracking.
 *
 * - Keyed by `cardKey` (e.g. `round_playerIndex`) so every player receives a completely fresh,
 *   covered card with zero carryover of the previous player's revealed state.
 * - Auto-reveals once >= 42% of the card surface grid cells have been scratched.
 * - Provides an accessible one-tap fallback button (`اكشف الكارت بضغطة`) if touch scratching isn't convenient.
 */
@Composable
fun SecretScratchCard(
    cardKey: String,
    playerName: String,
    isSpy: Boolean,
    secretWord: String,
    categoryTitle: String,
    isRevealed: Boolean,
    onAutoRevealed: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Key all internal scratch state strictly to `cardKey` so switching to the next player resets everything cleanly
    val strokePoints = remember(cardKey) { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember(cardKey) { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember(cardKey) { mutableStateOf(IntSize.Zero) }
    val scratchedCells = remember(cardKey) { mutableSetOf<Int>() }
    var scratchProgressPercent by remember(cardKey) { mutableIntStateOf(0) }

    val gridCols = 10
    val gridRows = 8
    val totalCells = gridCols * gridRows

    fun markScratchedArea(point: Offset) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0 || isRevealed) return
        val cellW = canvasSize.width.toFloat() / gridCols
        val cellH = canvasSize.height.toFloat() / gridRows
        val centerCol = (point.x / cellW).toInt()
        val centerRow = (point.y / cellH).toInt()

        // Brush radius covers neighboring cells
        for (dc in -1..1) {
            for (dr in -1..1) {
                val c = centerCol + dc
                val r = centerRow + dr
                if (c in 0 until gridCols && r in 0 until gridRows) {
                    scratchedCells.add(r * gridCols + c)
                }
            }
        }
        val pct = ((scratchedCells.size.toFloat() / totalCells.toFloat()) * 100f).toInt()
        scratchProgressPercent = pct
        if (pct >= 42 && !isRevealed) {
            onAutoRevealed()
        }
    }

    LaunchedEffect(cardKey) {
        strokePoints.clear()
        currentStroke = emptyList()
        scratchedCells.clear()
        scratchProgressPercent = 0
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.widthIn(max = 420.dp)
    ) {
        Card(
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(
                width = 2.dp,
                color = if (isRevealed) {
                    if (isSpy) SpyCrimson else EmeraldConfirm
                } else {
                    SpyGold.copy(alpha = 0.75f)
                }
            ),
            colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp)
                .testTag("secret_scratch_card")
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
            ) {
                // UNDERNEATH LAYER: Secret Role / Secret Word Content
                // Only rendered if user started scratching or revealed, preventing accidental flash during player transition!
                val showUnderneath = isRevealed || strokePoints.isNotEmpty() || currentStroke.isNotEmpty()
                if (showUnderneath) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = if (isSpy) {
                                    Brush.radialGradient(
                                        listOf(Color(0xFF450A0A), Color(0xFF1A080C))
                                    )
                                } else {
                                    Brush.radialGradient(
                                        listOf(Color(0xFF064E3B), Color(0xFF0B192C))
                                    )
                                }
                            )
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isSpy) Icons.Filled.WarningAmber else Icons.Filled.Key,
                                contentDescription = null,
                                tint = if (isSpy) SpyCrimson else SpyGold,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "التصنيف: $categoryTitle",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            if (isSpy) {
                                // Exact literal phrase mandated by prompt Section 6 & Section 11:
                                Text(
                                    text = SpyGameEngine.SPY_SECRET_MESSAGE,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color(0xFFFFCDD2),
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.testTag("spy_secret_message_text")
                                )
                            } else {
                                Text(
                                    text = "الكلمة السرية هي:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = secretWord,
                                    style = MaterialTheme.typography.displayMedium,
                                    color = SpyGold,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.testTag("citizen_secret_word_text")
                                )
                            }
                        }
                    }
                }

                // TOP LAYER: Metallic Gold-Foil Scratch Coating
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isRevealed,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.large)
                            .pointerInput(cardKey) {
                                detectTapGestures { offset ->
                                    strokePoints.add(listOf(offset, Offset(offset.x + 2f, offset.y + 2f)))
                                    markScratchedArea(offset)
                                }
                            }
                            .pointerInput(cardKey) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        currentStroke = listOf(startOffset)
                                        markScratchedArea(startOffset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val pos = change.position
                                        currentStroke = currentStroke + pos
                                        markScratchedArea(pos)
                                    },
                                    onDragEnd = {
                                        if (currentStroke.isNotEmpty()) {
                                            strokePoints.add(currentStroke)
                                            currentStroke = emptyList()
                                        }
                                    },
                                    onDragCancel = {
                                        if (currentStroke.isNotEmpty()) {
                                            strokePoints.add(currentStroke)
                                            currentStroke = emptyList()
                                        }
                                    }
                                )
                            }
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        ) {
                            // Draw metallic scratch foil surface
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF9A7B2C),
                                        Color(0xFFD4AF37),
                                        Color(0xFFF3CF55),
                                        Color(0xFFB58E24),
                                        Color(0xFF7D6018)
                                    ),
                                    start = Offset.Zero,
                                    end = Offset(size.width, size.height)
                                )
                            )

                            // Subtle security crosshatch lines on foil
                            val step = 28.dp.toPx()
                            var x = 0f
                            while (x < size.width + size.height) {
                                drawLine(
                                    color = Color.Black.copy(alpha = 0.08f),
                                    start = Offset(x, 0f),
                                    end = Offset(x - size.height, size.height),
                                    strokeWidth = 2f
                                )
                                x += step
                            }

                            // Punch erased strokes through foil using BlendMode.Clear
                            val brushWidth = 54.dp.toPx()
                            val allStrokes = if (currentStroke.isNotEmpty()) {
                                strokePoints + listOf(currentStroke)
                            } else {
                                strokePoints
                            }

                            for (pts in allStrokes) {
                                if (pts.size == 1) {
                                    drawCircle(
                                        color = Color.Transparent,
                                        radius = brushWidth / 2f,
                                        center = pts.first(),
                                        blendMode = BlendMode.Clear
                                    )
                                } else if (pts.size > 1) {
                                    val path = Path().apply {
                                        moveTo(pts.first().x, pts.first().y)
                                        for (i in 1 until pts.size) {
                                            lineTo(pts[i].x, pts[i].y)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color.Transparent,
                                        style = Stroke(
                                            width = brushWidth,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        ),
                                        blendMode = BlendMode.Clear
                                    )
                                }
                            }
                        }

                        if (scratchProgressPercent < 15) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Fingerprint,
                                    contentDescription = null,
                                    tint = Color(0xFF0B111E),
                                    modifier = Modifier.size(52.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "اخدش الكارت بإصبعك لكشف دورك السري",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFF0B111E),
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "سري للغاية — خاص بـ $playerName فقط",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF1E293B),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Accessible instant-reveal fallback button mandated by Section 6
        if (!isRevealed) {
            OutlinedButton(
                onClick = onAutoRevealed,
                border = BorderStroke(1.dp, SpyGold.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("instant_reveal_card_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Visibility,
                        contentDescription = null,
                        tint = SpyGold
                    )
                    Text(
                        text = "كشف فوري للكارت (بديل الخدش)",
                        color = SpyGold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
