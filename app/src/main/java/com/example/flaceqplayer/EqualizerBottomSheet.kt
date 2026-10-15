package com.example.flaceqplayer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerBottomSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isEnabled by remember { mutableStateOf(EqualizerHelper.isEnabled) }

    // ── Reactive band list (updates on session init & preset apply) ────────
    val bands by EqualizerHelper.bandsState.collectAsState()

    // ── Per-band independent float states ──────────────────────────────────
    // ROOT CAUSE FIX for "slider can't be dragged":
    // Previously each onValueChange call assigned `bands = ...` → triggered global
    // recomposition → Slider lost its internal drag gesture state → snapped back.
    //
    // Fix: each band has its OWN mutableFloatStateOf. Dragging updates only that
    // one state. No global recomposition. Only the hardware EQ is updated.
    // The EQ canvas visualizer reads these values directly → smooth real-time curve.
    val bandSliderValues: SnapshotStateList<Float> = remember { mutableStateListOf() }

    // Sync slider values whenever bands change (session start or preset applied)
    LaunchedEffect(bands) {
        if (bandSliderValues.size != bands.size) {
            // New session: full initialise
            bandSliderValues.clear()
            bands.forEach { bandSliderValues.add(it.currentLevelMb.toFloat()) }
        } else {
            // Preset applied: update only bands that differ from slider position
            bands.forEachIndexed { i, band ->
                val sliderMb = bandSliderValues.getOrNull(i)?.toInt()?.toShort()
                if (sliderMb != band.currentLevelMb) {
                    bandSliderValues[i] = band.currentLevelMb.toFloat()
                }
            }
        }
    }

    val minMb = EqualizerHelper.minLevelMb.toFloat()
    val maxMb = EqualizerHelper.maxLevelMb.toFloat()
    val primary = MaterialTheme.colorScheme.primary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF14151C),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {

            // ── Header ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hardware Equalizer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "AudioFX DSP Engine",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = {
                        isEnabled = it
                        EqualizerHelper.setEnabled(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = primary
                    )
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── EQ Curve Canvas ────────────────────────────────────────────
            EqCurveCanvas(
                bandValues = bandSliderValues.toList(),
                minMb = minMb,
                maxMb = maxMb,
                primaryColor = primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            )

            Spacer(Modifier.height(20.dp))

            // ── Band Sliders ───────────────────────────────────────────────
            if (bands.isEmpty()) {
                Text(
                    text = "Equalizer activates once a FLAC audio session begins.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                bands.forEachIndexed { index, band ->
                    if (index >= bandSliderValues.size) return@forEachIndexed

                    val freqLabel = if (band.centerFreqHz >= 1000)
                        "${band.centerFreqHz / 1000}k" else "${band.centerFreqHz}Hz"
                    val currentMb  = bandSliderValues[index]
                    val gainDb     = currentMb / 100f
                    val gainColor  = when {
                        gainDb >  0.5f -> primary
                        gainDb < -0.5f -> Color(0xFFFF6E6E)
                        else           -> Color(0xFF8A8D9E)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Frequency label (left)
                        Text(
                            text = freqLabel,
                            modifier = Modifier.width(44.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray,
                            fontWeight = FontWeight.Medium
                        )

                        // ── THE FIX: slider reads/writes its OWN state ─────
                        // No global recomposition → gesture is never interrupted
                        Slider(
                            value = currentMb,
                            onValueChange = { newVal ->
                                if (index < bandSliderValues.size) {
                                    bandSliderValues[index] = newVal          // local UI
                                    EqualizerHelper.setBandGainMb(           // hardware
                                        index, newVal.toInt().toShort()
                                    )
                                }
                            },
                            valueRange = minMb..maxMb,
                            enabled = isEnabled,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = gainColor,
                                activeTrackColor = gainColor.copy(alpha = 0.85f),
                                inactiveTrackColor = Color(0xFF2A2D3A),
                                disabledThumbColor = Color(0xFF4A4D5E),
                                disabledActiveTrackColor = Color(0xFF3A3D4E)
                            )
                        )

                        // dB value (right)
                        Text(
                            text = String.format("%+.1f", gainDb),
                            modifier = Modifier.width(52.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = gainColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Preset Chips ───────────────────────────────────────────────
            Text(
                text = "Presets",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF8A8D9E)
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EqualizerHelper.defaultPresets) { preset ->
                    SuggestionChip(
                        onClick = {
                            EqualizerHelper.applyPreset(preset)
                            // LaunchedEffect(bands) will sync bandSliderValues automatically
                            // because applyPreset() updates EqualizerHelper.bandsState
                        },
                        label = { Text(preset.name, fontSize = 12.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Color(0xFF222430),
                            labelColor = Color.White
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = Color(0xFF3A3D50)
                        )
                    )
                }
            }
        }
    }
}

// ── EQ Curve Canvas ────────────────────────────────────────────────────────
// Lightweight Canvas drawing: bezier curve + filled gradient area + band dots.
// Reads bandSliderValues list (smooth float, not quantised Short).
@Composable
private fun EqCurveCanvas(
    bandValues: List<Float>,
    minMb: Float,
    maxMb: Float,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val gridColor = Color(0xFF2E3142)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val totalRange = (maxMb - minMb).coerceAtLeast(1f)
        val midY = h * (maxMb / totalRange)  // y-position of 0 dB line

        // ── Grid lines ──
        listOf(0.25f, 0.5f, 0.75f).forEach { ratio ->
            drawLine(
                color = gridColor.copy(alpha = 0.6f),
                start = Offset(0f, h * ratio),
                end = Offset(w, h * ratio),
                strokeWidth = 0.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
            )
        }
        // 0 dB line (solid, brighter)
        drawLine(
            color = gridColor.copy(alpha = 1f),
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 1.dp.toPx()
        )

        if (bandValues.size < 2) return@Canvas

        // ── Compute point positions ──
        val stepX = w / (bandValues.size - 1).toFloat()
        val points = bandValues.mapIndexed { i, mb ->
            val normalized = (mb - minMb) / totalRange  // 0 = bottom, 1 = top
            Offset(x = i * stepX, y = h * (1f - normalized))
        }

        // ── Filled area (bezier path from 0-dB baseline) ──
        val fillPath = Path().apply {
            moveTo(points.first().x, midY)
            for (i in 1 until points.size) {
                val p = points[i - 1]; val q = points[i]
                val cx = (p.x + q.x) / 2f
                cubicTo(cx, p.y, cx, q.y, q.x, q.y)
            }
            lineTo(points.last().x, midY)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.28f),
                    primaryColor.copy(alpha = 0.04f)
                ),
                startY = 0f, endY = h
            )
        )

        // ── Bezier line ──
        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val p = points[i - 1]; val q = points[i]
                val cx = (p.x + q.x) / 2f
                cubicTo(cx, p.y, cx, q.y, q.x, q.y)
            }
        }
        drawPath(
            path = linePath,
            color = primaryColor.copy(alpha = 0.9f),
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // ── Band dots ──
        points.forEach { p ->
            drawCircle(color = primaryColor,     radius = 4.5.dp.toPx(), center = p)
            drawCircle(color = Color(0xFF14151C), radius = 2.5.dp.toPx(), center = p)
        }
    }
}
