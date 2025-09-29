package com.example.scannerpro.Collage

import Document
import DocumentRepository
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

object CollageSaver {

    suspend fun handleSaveCollage(
        context: Context,
        repository: DocumentRepository,
        name: String,
        pages: List<CollagePageData>,
        watermark: WatermarkData?,
        pageSize: PageSize,
        canvasWidthPx: Float,
        density: Density
    ) {
        if (pages.isEmpty() || canvasWidthPx <= 0f) return

        val newDocument = Document(name = name)
        val documentId = repository.insertDocument(newDocument)

        pages.forEach { pageData ->
            val finalBitmap = renderPageToBitmap(pageData, watermark, pageSize, canvasWidthPx, density)
            repository.addPageToDocument(documentId, finalBitmap)
        }
    }

    private fun renderPageToBitmap(
        pageData: CollagePageData,
        watermark: WatermarkData?,
        pageSize: PageSize,
        canvasWidthPx: Float,
        density: Density
    ): Bitmap {
        val canvasHeightPx = (canvasWidthPx / pageSize.aspectRatio).roundToInt()
        val finalBitmap = Bitmap.createBitmap(canvasWidthPx.roundToInt(), canvasHeightPx, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(finalBitmap)

        // Dibuja el fondo blanco
        canvas.drawColor(android.graphics.Color.WHITE)

        // Dibuja cada imagen del collage en su posición
        pageData.items.forEach { item ->
            canvas.drawBitmap(
                item.bitmap, null, android.graphics.Rect(
                    item.offset.x.roundToInt(),
                    item.offset.y.roundToInt(),
                    (item.offset.x + item.size.width).roundToInt(),
                    (item.offset.y + item.size.height).roundToInt()
                ), null
            )
        }

        // --- INICIO DE LA CORRECCIÓN ---
        // Dibuja la marca de agua, AHORA con la lógica de patrón completa
        watermark?.let {
            val paint = Paint().apply {
                color = it.color.copy(alpha = it.opacity).toArgb()
                textSize = with(density) { it.size.sp.toPx() }
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }

            val textBaseline = -(paint.ascent() + paint.descent()) / 2f
            val textWidth = paint.measureText(it.text)
            val textHeight = paint.descent() - paint.ascent()
            val centerOffset = if (it.offset == Offset.Zero) Offset(canvasWidthPx / 2f, canvasHeightPx.toFloat() / 2f) else it.offset

            if (it.isPattern) {
                canvas.save()
                canvas.translate(centerOffset.x, centerOffset.y)
                canvas.rotate(it.rotation)

                val spacingX = textWidth * 1.5f
                val spacingY = textHeight * 3f

                val halfW = (textWidth + 24f) / 2f
                val halfH = (textHeight + 24f) / 2f

                val coverHalfWidth = canvasWidthPx * 1.5f
                val coverHalfHeight = canvasHeightPx * 1.5f
                val endX = canvasWidthPx * 2.5f
                val endY = canvasHeightPx * 2.5f

                val leftCells = kotlin.math.ceil(coverHalfWidth / spacingX).toInt()
                val topCells = kotlin.math.ceil(coverHalfHeight / spacingY).toInt()
                val startX = -leftCells * spacingX
                val startY = -topCells * spacingY

                var y = startY
                while (y < endY) {
                    var x = startX
                    while (x < endX) {
                        if (!(x >= -halfW && x <= halfW && y >= -halfH && y <= halfH)) {
                            canvas.drawText(it.text, x, y + textBaseline, paint)
                        }
                        x += spacingX
                    }
                    y += spacingY
                }
                canvas.restore()
            }

            // Dibuja siempre la instancia central
            canvas.save()
            canvas.translate(centerOffset.x, centerOffset.y)
            canvas.rotate(it.rotation)
            canvas.drawText(it.text, 0f, textBaseline, paint)
            canvas.restore()
        }
        // --- FIN DE LA CORRECCIÓN ---

        return finalBitmap
    }
}

