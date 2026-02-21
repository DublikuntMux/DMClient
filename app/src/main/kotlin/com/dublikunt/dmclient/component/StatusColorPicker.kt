package com.dublikunt.dmclient.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun StatusColorPicker(
    color: Long,
    onColorChange: (Long) -> Unit
) {
    var redChannel by remember(color) {
        mutableFloatStateOf(((color ushr 16) and 0xFF).toFloat())
    }
    var greenChannel by remember(color) {
        mutableFloatStateOf(((color ushr 8) and 0xFF).toFloat())
    }
    var blueChannel by remember(color) {
        mutableFloatStateOf((color and 0xFF).toFloat())
    }

    fun publishColor() {
        onColorChange(
            argbToLong(
                255,
                redChannel.roundToInt(),
                greenChannel.roundToInt(),
                blueChannel.roundToInt()
            )
        )
    }

    val selectedColorPreview = Color(
        red = redChannel / 255f,
        green = greenChannel / 255f,
        blue = blueChannel / 255f
    )
    val selectedColorLong = argbToLong(
        255,
        redChannel.roundToInt(),
        greenChannel.roundToInt(),
        blueChannel.roundToInt()
    )
    val selectedColorHex =
        "#" + (selectedColorLong and 0xFFFFFFL).toString(16).uppercase().padStart(6, '0')

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Color", style = MaterialTheme.typography.labelLarge)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    color = selectedColorPreview,
                    shape = RoundedCornerShape(8.dp)
                )
        )
        Text(
            text = selectedColorHex,
            style = MaterialTheme.typography.bodyMedium
        )

        ColorChannelSlider(
            label = "Red",
            value = redChannel,
            onValueChange = {
                redChannel = it
                publishColor()
            }
        )
        ColorChannelSlider(
            label = "Green",
            value = greenChannel,
            onValueChange = {
                greenChannel = it
                publishColor()
            }
        )
        ColorChannelSlider(
            label = "Blue",
            value = blueChannel,
            onValueChange = {
                blueChannel = it
                publishColor()
            }
        )
    }
}

@Composable
private fun ColorChannelSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label)
            Text(value.roundToInt().toString())
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..255f
        )
    }
}

private fun argbToLong(alpha: Int, red: Int, green: Int, blue: Int): Long {
    return ((alpha.toLong() and 0xFF) shl 24) or
            ((red.toLong() and 0xFF) shl 16) or
            ((green.toLong() and 0xFF) shl 8) or
            (blue.toLong() and 0xFF)
}