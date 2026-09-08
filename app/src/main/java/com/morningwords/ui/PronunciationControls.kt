package com.morningwords.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.morningwords.speech.*

internal data class Pronunciation(
    val accent: EnglishAccent,
    val player: SpeechPlayer,
    val speak: (String, EnglishAccent) -> Unit,
    val selectAccent: (EnglishAccent) -> Unit,
)

/** Manual accents are local to this word; global defaults remain owned by SettingsRepository. */
@Composable
internal fun rememberPronunciation(source: SpeechSource, defaultAccent: EnglishAccent, installed: Boolean, wordKey: Any = Unit): Pronunciation {
    var accent by remember(source, defaultAccent, wordKey) { mutableStateOf(defaultAccent) }
    var pending by remember(source, defaultAccent, wordKey) { mutableStateOf<Pair<Int, String>?>(null) }
    var serial by remember { mutableIntStateOf(0) }
    // Only one engine lives on a screen. Switching closes the old native model before loading the next.
    val player = rememberSpeechPlayer(source, accent, installed)
    DisposableEffect(player, defaultAccent, wordKey) {
        player.stop()
        onDispose { player.stop() }
    }
    LaunchedEffect(player, player.ready, pending) {
        if (player.ready) pending?.let {
            pending = null
            player.speak(it.second)
        }
    }
    return Pronunciation(accent, player, speak = { text, requested ->
        player.stop()
        accent = requested
        pending = ++serial to text
    }, selectAccent = { requested ->
        player.stop()
        pending = null
        accent = requested
    })
}

@Composable
internal fun PronunciationButtons(enabled: Boolean, onSpeak: (EnglishAccent) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (accent in EnglishAccent.entries) {
            FilledTonalButton(
                onClick = { onSpeak(accent) }, enabled = enabled,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "播放${accent.label}" },
            ) {
                Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text(accent.label)
            }
        }
    }
}
