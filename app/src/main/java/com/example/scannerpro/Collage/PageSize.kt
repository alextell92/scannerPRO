package com.example.scannerpro.Collage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PageSize(val name: String, val aspectRatio: Float)

object PageSizes {
    // CAMBIO: Se agregan nuevos tamaños de página estándar ISO
    val A3 = PageSize("A3", 1f / 1.414f)
    val A4 = PageSize("A4", 1f / 1.414f)
    val A5 = PageSize("A5", 1f / 1.414f)
    val B4 = PageSize("B4", 1f / 1.414f)
    val B5 = PageSize("B5", 1f / 1.414f)
    val Oficio = PageSize("Oficio", 1f / 1.545f)
    val Carta = PageSize("Carta", 1f / 1.294f)
    val A4Horizontal = PageSize("A4 Horiz", 1.414f)
    val default = A4
    // CAMBIO: Se agregan los nuevos tamaños a la lista para que aparezcan en la UI
    val all = listOf(A3, A4, A5, B4, B5, Oficio, Carta, A4Horizontal)
}

@Composable
internal fun CollagePageSizeSelectionRow(currentSize: PageSize, onSizeSelected: (PageSize) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(PageSizes.all) { size ->
            val isSelected = size.name == currentSize.name
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
                    .border(2.dp, if (isSelected) Color.Green else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onSizeSelected(size) }
                    .padding(vertical = 8.dp, horizontal = 4.dp)
                    .width(60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PageSizeIcon(pageSize = size, isSelected = isSelected)
                Spacer(Modifier.height(4.dp))
                Text(
                    size.name,
                    color = if (isSelected) Color.Green else Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    maxLines = 2,
                    lineHeight = 12.sp
                )
            }
        }
    }
}

@Composable
internal fun PageSizeIcon(pageSize: PageSize, isSelected: Boolean = true) {
    val color = if (isSelected) Color.Green else Color.White
    Canvas(modifier = Modifier.size(24.dp)) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val aspectRatio = pageSize.aspectRatio

        val rectSize = if (aspectRatio > 1) { // Horizontal
            Size(canvasWidth * 0.9f, (canvasWidth * 0.9f) / aspectRatio)
        } else { // Vertical
            Size((canvasHeight * 0.9f) * aspectRatio, canvasHeight * 0.9f)
        }

        val topLeft = Offset(
            (canvasWidth - rectSize.width) / 2,
            (canvasHeight - rectSize.height) / 2
        )

        drawRect(
            color = color,
            topLeft = topLeft,
            size = rectSize,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

