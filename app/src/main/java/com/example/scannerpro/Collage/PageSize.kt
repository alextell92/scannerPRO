package com.example.scannerpro.Collage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt

/**
 * Data class to represent a page size with its name and aspect ratio.
 */


/**
 * A horizontal scrolling row for selecting a page size.
 * @param currentSize The currently selected PageSize.
 * @param onSizeSelected Callback function for when a new size is selected.
 */
@Composable
fun PageSizeSelectionRow(
    currentSize: PageSize,
    onSizeSelected: (PageSize) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(PageSizes.all) { size ->
            val isSelected = size.name == currentSize.name
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
                    .border(
                        2.dp,
                        if (isSelected) Color.Green else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSizeSelected(size) }
                    .padding(vertical = 8.dp, horizontal = 16.dp), // More horizontal padding for text
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = size.name,
                    color = if (isSelected) Color.Green else Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }
        }
    }
}
