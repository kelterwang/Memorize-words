package com.morningwords.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morningwords.domain.model.WordCard

@Composable
internal fun StudyWordCard(
    card: WordCard,
    answerShown: Boolean,
    largeFont: Boolean,
    pronunciationReady: Boolean,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val sage = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            VocabularyHeading(card.word, VocabularyHeadingSize.STUDY, largeFont = largeFont)
            card.phonetic?.let { Text(it, color = ink.copy(alpha = .5f), modifier = Modifier.padding(top = 8.dp)) }
            IconButton(onClick = onSpeak, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.AutoMirrored.Outlined.VolumeUp, "发音", tint = if (pronunciationReady) sage else Color.Gray)
            }
            if (answerShown) {
                HorizontalDivider(Modifier.padding(vertical = 22.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .45f))
                card.partOfSpeech?.let { Text(it, color = sage, fontWeight = FontWeight.Bold) }
                card.requiredMeaning?.let {
                    Text(it, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                }
                if (card.meaning != card.requiredMeaning) card.meaning?.let {
                    Text(it, textAlign = TextAlign.Center, color = ink.copy(alpha = .7f), modifier = Modifier.padding(top = 8.dp))
                }
                card.example?.let {
                    Text(highlightedExample(it, card.word), textAlign = TextAlign.Center, color = ink.copy(alpha = .6f), modifier = Modifier.padding(top = 12.dp))
                }
            } else {
                Text("想好后再作答", color = ink.copy(alpha = .38f), modifier = Modifier.padding(top = 28.dp))
            }
        }
    }
}
