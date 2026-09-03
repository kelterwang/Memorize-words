package com.morningwords.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

internal enum class VocabularyHeadingSize { STUDY, DETAIL, LIST }

@Composable
internal fun VocabularyHeading(
    text: String,
    size: VocabularyHeadingSize,
    modifier: Modifier = Modifier,
    largeFont: Boolean = false,
) {
    val isPhrase = text.any(Char::isWhitespace)
    val fontSize = when (size) {
        VocabularyHeadingSize.LIST -> 20
        VocabularyHeadingSize.DETAIL -> if (isPhrase) 32 else 38
        VocabularyHeadingSize.STUDY -> when {
            isPhrase && text.length > 20 -> 32
            isPhrase -> 36
            else -> 42
        } + if (largeFont) 6 else 0
    }
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = fontSize.sp,
            // Override the inherited body line height whenever the heading grows.
            lineHeight = (fontSize * 1.3f).sp,
            letterSpacing = 0.sp,
            fontWeight = if (isPhrase || size == VocabularyHeadingSize.LIST) FontWeight.SemiBold else FontWeight.Bold,
            textAlign = if (size == VocabularyHeadingSize.LIST) TextAlign.Start else TextAlign.Center,
            lineBreak = LineBreak.Heading,
            hyphens = Hyphens.None,
        ),
    )
}
