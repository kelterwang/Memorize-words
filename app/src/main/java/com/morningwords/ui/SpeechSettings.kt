package com.morningwords.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.morningwords.speech.*

@Composable
internal fun rememberSpeechPlayer(source: SpeechSource, accent: EnglishAccent, installed: Boolean): SpeechPlayer {
    val context = LocalContext.current
    val player = remember(source, accent, installed) { SpeechPlayer(context, source, accent) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(player, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.stop() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); player.close() }
    }
    return player
}

@Composable
internal fun SpeechSettings(state: AppUiState, vm: AppViewModel) {
    val context = LocalContext.current
    var showLicenses by remember { mutableStateOf(false) }
    if (showLicenses) {
        val notices = remember { context.assets.open("speech-notices.txt").bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { showLicenses = false }, title = { Text("离线语音开源许可") },
            text = { Text(notices, modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text("关闭") } })
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importVoicePack) }
    val player = rememberSpeechPlayer(state.settings.speechSource, state.settings.englishAccent, state.voicePackInstalled)
    SettingsGroup("英文发音 · 1 倍速") {
        SettingsChoice("手机自带发音", state.settings.speechSource == SpeechSource.SYSTEM) { vm.setSpeechSource(SpeechSource.SYSTEM) }
        HorizontalDivider()
        SettingsChoice("Kokoro 离线发音", state.settings.speechSource == SpeechSource.KOKORO) { vm.setSpeechSource(SpeechSource.KOKORO) }
        HorizontalDivider()
        SettingsChoice("美音 · American English", state.settings.englishAccent == EnglishAccent.US) { vm.setEnglishAccent(EnglishAccent.US) }
        SettingsChoice("英音 · British English", state.settings.englishAccent == EnglishAccent.UK) { vm.setEnglishAccent(EnglishAccent.UK) }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("离线发音包", style = MaterialTheme.typography.titleMedium)
            if (state.voicePackInstalled) {
                Text("Kokoro v1.0 英语包 · 已导入\n美音 Heart / 英音 Emma", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("尚未导入 Kokoro 语音包。选择配套 ZIP 文件后，美音、英音均可断网使用。", style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }, enabled = !state.voicePackImporting) {
                Text(if (state.voicePackImporting) "正在导入并校验…" else "选择离线语音包（ZIP）")
            }
            if (state.voicePackImporting) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("请保持 App 打开，导入约需 200 MB 可用空间。") }
            if (state.settings.speechSource == SpeechSource.SYSTEM) {
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
                        .onFailure { vm.showMessage("无法打开系统语音设置，请手动进入手机设置 → 文字转语音") }
                }) { Text("管理手机语音数据") }
            }
            Text(player.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { showLicenses = true }) { Text("离线语音开源许可") }
            OutlinedButton(onClick = { player.speak("Apple. Tomato. Schedule. A fresh start every morning.") }, enabled = player.ready && !state.voicePackImporting) { Text("试听发音") }
        }
    }
}
