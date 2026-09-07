package com.morningwords.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Psychology
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
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, StitchSand.copy(alpha = .4f)), shadowElevation = 1.dp,
        modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (answerShown) Arrangement.Top else Arrangement.Center) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                StudyChip(if (answerShown) "答案核对" else "专注回忆", sage, Color(0xFFE2ECE9))
            }
            Spacer(Modifier.height(24.dp))
            VocabularyHeading(card.word, VocabularyHeadingSize.STUDY, largeFont = largeFont)
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                card.phonetic?.let { Text(it, color = StitchMuted, modifier = Modifier.weight(1f, fill = false), textAlign = TextAlign.Center) }
                FilledTonalIconButton(onClick = onSpeak, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = StitchSand)) {
                    Icon(Icons.AutoMirrored.Outlined.VolumeUp, "发音", tint = if (pronunciationReady) sage else Color.Gray)
                }
            }
            if (answerShown) {
                HorizontalDivider(Modifier.padding(vertical = 20.dp), color = StitchSand)
                Surface(color = Color(0xFFFBF7F0), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        card.partOfSpeech?.let { StudyChip(it, Color(0xFF3D6B5E), Color(0xFFE2ECE9)) }
                        Text(card.requiredMeaning ?: card.meaning ?: "暂无释义", fontSize = 21.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
                        if (card.meaning != card.requiredMeaning && card.requiredMeaning != null) card.meaning?.let {
                            Text(it, color = StitchMuted, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                card.example?.let {
                    Spacer(Modifier.height(16.dp))
                    Surface(color = Color(0xFFFFF9F1), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = sage, modifier = Modifier.size(20.dp))
                                Text("例句", modifier = Modifier.padding(start = 8.dp), color = StitchMuted, fontWeight = FontWeight.SemiBold)
                            }
                            Text(highlightedExample(it, card.word), color = ink, fontSize = 17.sp, lineHeight = 27.sp)
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(28.dp))
                Surface(shape = RoundedCornerShape(50), color = StitchSand.copy(alpha = .7f)) {
                    Icon(Icons.Outlined.Psychology, null, tint = StitchMuted, modifier = Modifier.padding(15.dp).size(24.dp))
                }
                Text("在心中默背释义与拼写\n点击下方按钮检验记忆", color = StitchMuted, textAlign = TextAlign.Center,
                    lineHeight = 26.sp, modifier = Modifier.padding(top = 16.dp))
                Text("RECALL PHASE", color = StitchMuted, letterSpacing = 2.sp, fontSize = 11.sp, modifier = Modifier.padding(top = 24.dp))
            }
        }
    }
}
