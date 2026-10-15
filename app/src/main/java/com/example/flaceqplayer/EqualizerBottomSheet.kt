package com.example.flaceqplayer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerBottomSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isEnabled by remember { mutableStateOf(EqualizerHelper.isEnabled) }
    var bands by remember { mutableStateOf(EqualizerHelper.bands.toList()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF14151C),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // Header Row: Title & Toggle Switch
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
                        text = "Lossless AudioFX DSP Engine",
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
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Presets Row
            Text(
                text = "Preset Sound Profiles",
                style = MaterialTheme.typography.labelMedium,
                color = Color.LightGray
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(EqualizerHelper.defaultPresets) { preset ->
                    SuggestionChip(
                        onClick = {
                            EqualizerHelper.applyPreset(preset)
                            bands = EqualizerHelper.bands.toList()
                        },
                        label = { Text(preset.name, fontSize = 12.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Color(0xFF222430),
                            labelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sliders for each frequency band
            Text(
                text = "Frequency Response Bands",
                style = MaterialTheme.typography.labelMedium,
                color = Color.LightGray
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (bands.isEmpty()) {
                Text(
                    text = "Equalizer will activate once a FLAC audio session begins.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                bands.forEachIndexed { index, band ->
                    val freqLabel = if (band.centerFreqHz >= 1000) {
                        "${band.centerFreqHz / 1000} kHz"
                    } else {
                        "${band.centerFreqHz} Hz"
                    }
                    val gainDb = band.gainDb

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = freqLabel,
                            modifier = Modifier.width(60.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Slider(
                            value = band.currentLevelMb.toFloat(),
                            onValueChange = { newVal ->
                                EqualizerHelper.setBandGainMb(index, newVal.toInt().toShort())
                                bands = EqualizerHelper.bands.toList()
                            },
                            valueRange = EqualizerHelper.minLevelMb.toFloat()..EqualizerHelper.maxLevelMb.toFloat(),
                            enabled = isEnabled,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = String.format("%+.1f dB", gainDb),
                            modifier = Modifier.width(65.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (gainDb > 0) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
