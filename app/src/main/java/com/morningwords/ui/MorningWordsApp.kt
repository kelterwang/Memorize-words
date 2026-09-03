@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.morningwords.ui

import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.morningwords.data.entity.WrongWordRow
import com.morningwords.data.repository.SessionView
import com.morningwords.domain.importer.ImportPreview
import com.morningwords.domain.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF292621)
private val Cream = Color(0xFFFFF9F1)
private val Paper = Color(0xFFFFFDF9)
private val Sage = Color(0xFF54715A)
private val SageSoft = Color(0xFFDDE8DA)
private val Coral = Color(0xFFB95F4B)
private val Gold = Color(0xFFE7B75D)
private val ExampleWordRed = Color(0xFFD32F2F)

@Composable
fun MorningWordsApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Sage, onPrimary = Color.White, primaryContainer = SageSoft,
            secondary = Coral, background = Cream, surface = Paper, onSurface = Ink,
            error = Color(0xFFB3261E), outline = Color(0xFFD1C8BA),
        ),
        typography = Typography(
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
        ),
    ) {
        val route = nav.currentBackStackEntryAsState().value?.destination?.route
        val rootRoutes = setOf("home", "library", "wrong", "settings")
        Scaffold(
            containerColor = Cream,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { if (route in rootRoutes) AppBottomBar(nav, route ?: "home") },
        ) { padding ->
            NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
                composable("home") { HomeScreen(state, viewModel, nav) }
                composable("library") { LibraryScreen(state, viewModel, nav) }
                composable("batch/{batchId}") { entry ->
                    val batchId = entry.arguments?.getString("batchId")?.toLongOrNull() ?: return@composable
                    LaunchedEffect(batchId) { viewModel.loadBatchDetail(batchId) }
                    BatchDetailScreen(batchId, state, nav)
                }
                composable("import") { ImportScreen(state.importPreview, state.isBusy, viewModel, nav) }
                composable("setup") { SetupScreen(state, viewModel, nav) }
                composable("test/{sessionId}") { entry ->
                    val id = entry.arguments?.getString("sessionId")?.toLongOrNull() ?: return@composable
                    LaunchedEffect(id) { viewModel.loadSession(id) }
                    TestScreen(state, viewModel, nav)
                }
                composable("completed") { CompletionScreen(state.session, nav) }
                composable("wrong") { WrongScreen(state, viewModel, nav) }
                composable("wrong/batch/{batchId}") { entry ->
                    val batchId = entry.arguments?.getString("batchId")?.toLongOrNull() ?: return@composable
                    WrongBatchScreen(state.wrongWordGroups.firstOrNull { it.batchId == batchId }, nav)
                }
                composable("wrong/word/{wordId}") { entry ->
                    val wordId = entry.arguments?.getString("wordId")?.toLongOrNull() ?: return@composable
                    WrongWordDetailScreen(state.wrongWords.firstOrNull { it.word.id == wordId }, nav)
                }
                composable("wrong/review") { WrongReviewSetup(state, viewModel, nav) }
                composable("settings") { SettingsScreen(state, viewModel) }
            }
        }
    }
}

@Composable
private fun AppBottomBar(nav: NavHostController, current: String) {
    val items = listOf(
        Triple("home", "今日", Icons.Outlined.WbSunny),
        Triple("library", "词库", Icons.Outlined.Book),
        Triple("wrong", "错词", Icons.Outlined.BookmarkBorder),
        Triple("settings", "我的", Icons.Outlined.PersonOutline),
    )
    NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
        items.forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = current == route,
                onClick = { nav.navigate(route) { popUpTo("home"); launchSingleTop = true } },
                icon = { Icon(icon, label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = SageSoft),
            )
        }
    }
}

@Composable
private fun HomeScreen(state: AppUiState, vm: AppViewModel, nav: NavHostController) {
    LaunchedEffect(Unit) { vm.refreshHomeMessage() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text(state.greeting, style = MaterialTheme.typography.labelLarge, color = Sage)
            Text(state.dailyQuote, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("词库", state.dashboard.wordCount.toString(), Modifier.weight(1f))
                StatTile("批次", state.dashboard.batchCount.toString(), Modifier.weight(1f))
                StatTile("待复习", state.dashboard.wrongCount.toString(), Modifier.weight(1f), Coral)
            }
        }
        item {
            Surface(shape = RoundedCornerShape(28.dp), color = Sage, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Outlined.AutoStories, null, tint = Gold, modifier = Modifier.size(34.dp))
                    Text(
                        if (state.activeSessionId != null) "你有一场晨测\n正在进行" else if (state.dashboard.wordCount == 0) "还没有单词" else "准备好了吗？",
                        color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (state.activeSessionId != null) "进度已经自动保存，随时接着答。" else if (state.dashboard.wordCount == 0) "先导入老师要求背诵的单词。" else "选择昨天的批次，开始今天的检验。",
                        color = Color.White.copy(alpha = .78f),
                    )
                    Button(
                        onClick = {
                            state.activeSessionId?.let { nav.navigate("test/$it") }
                                ?: if (state.dashboard.wordCount == 0) nav.navigate("import") else nav.navigate("setup")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Ink),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.activeSessionId != null) "继续晨测" else if (state.dashboard.wordCount == 0) "导入单词" else "开始测试", modifier = Modifier.padding(6.dp)) }
                }
            }
        }
        item {
            SectionTitle("测试节奏", "离线保存 · 每题即时记录")
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepChip("01", "全部测试")
                StepChip("02", "轮次统计")
                StepChip("03", "按需复测")
            }
        }
    }
}

@Composable private fun StatTile(label: String, value: String, modifier: Modifier, accent: Color = Sage) {
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = Paper) {
        Column(Modifier.padding(16.dp)) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = accent)
            Text(label, style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = .58f))
        }
    }
}

@Composable private fun RowScope.StepChip(number: String, label: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = Paper, modifier = Modifier.weight(1f)) {
        Column(Modifier.padding(12.dp)) { Text(number, color = Gold, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun LibraryScreen(state: AppUiState, vm: AppViewModel, nav: NavHostController) {
    var editingBatch by remember { mutableStateOf<BatchSummary?>(null) }
    var editedName by remember { mutableStateOf("") }
    editingBatch?.let { batch ->
        AlertDialog(
            onDismissRequest = { editingBatch = null },
            title = { Text("修改词库名称") },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text("词库名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.renameBatch(batch.id, editedName); editingBatch = null },
                    enabled = editedName.isNotBlank() && !state.isBusy,
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingBatch = null }) { Text("取消") } },
        )
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        PageHeader("词库", "按老师布置的内容分批管理")
        Button(onClick = { nav.navigate("import") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("导入新批次")
        }
        Spacer(Modifier.height(14.dp))
        if (state.batches.isEmpty()) EmptyState(Icons.AutoMirrored.Outlined.MenuBook, "还没有批次", "从一行一个单词开始吧。")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(state.batches, key = { it.id }) { batch ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Paper,
                    modifier = Modifier.fillMaxWidth().clickable { nav.navigate("batch/${batch.id}") },
                ) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(14.dp), color = SageSoft) {
                            Icon(Icons.Outlined.Folder, null, tint = Sage, modifier = Modifier.padding(12.dp))
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(batch.name, fontWeight = FontWeight.SemiBold)
                            Text("${batch.wordCount} 个单词", style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = .55f))
                            Text("导入时间 ${formatImportTime(batch.createdAt)}", style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = .45f))
                        }
                        IconButton(onClick = { editingBatch = batch; editedName = batch.name }) { Icon(Icons.Outlined.Edit, "修改名称") }
                        IconButton(onClick = { vm.deleteBatch(batch.id) }) { Icon(Icons.Outlined.DeleteOutline, "删除") }
                        Icon(Icons.Outlined.ChevronRight, "查看词库详情", tint = Ink.copy(alpha = .45f))
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchDetailScreen(batchId: Long, state: AppUiState, nav: NavHostController) {
    val batch = state.batches.firstOrNull { it.id == batchId }
    var expandedWordIds by remember(batchId) { mutableStateOf(emptySet<Long>()) }
    Column(Modifier.fillMaxSize()) {
        BackHeader(batch?.name ?: "词库详情", nav)
        when {
            state.batchDetailId != batchId || state.isBatchDetailLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            batch == null -> EmptyState(Icons.Outlined.FolderOff, "这个词库已不存在", "返回词库列表查看其他文件夹。")
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text("${batch.wordCount} 个单词 · 导入于 ${formatImportTime(batch.createdAt)}", color = Ink.copy(alpha = .58f))
                    Text("点击单词查看完整词性、释义和例句", color = Sage, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp, bottom = 6.dp))
                }
                itemsIndexed(state.batchDetailWords, key = { _, word -> word.id }) { index, word ->
                    val expanded = word.id in expandedWordIds
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Paper,
                        modifier = Modifier.fillMaxWidth().clickable {
                            expandedWordIds = if (expanded) expandedWordIds - word.id else expandedWordIds + word.id
                        },
                    ) {
                        Column(Modifier.padding(17.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}", color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp))
                                Column(Modifier.weight(1f).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    VocabularyHeading(word.word, VocabularyHeadingSize.LIST)
                                    if (!expanded) Text(word.meaning ?: "暂无释义", color = Ink.copy(alpha = .58f), maxLines = 1)
                                }
                                word.partOfSpeech?.let { Text(it, color = Sage, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 6.dp)) }
                                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "收起" else "展开")
                            }
                            if (expanded) {
                                HorizontalDivider(Modifier.padding(vertical = 13.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .4f))
                                Text("词性", color = Sage, style = MaterialTheme.typography.labelMedium)
                                Text(word.partOfSpeech ?: "暂无词性", modifier = Modifier.padding(top = 3.dp))
                                Text("释义", color = Sage, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp))
                                Text(word.meaning ?: "暂无释义", modifier = Modifier.padding(top = 3.dp))
                                Text("例句", color = Sage, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp))
                                Text(
                                    word.example?.let { highlightedExample(it, word.word) } ?: AnnotatedString("暂无例句"),
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatImportTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun ImportScreen(preview: ImportPreview?, busy: Boolean, vm: AppViewModel, nav: NavHostController) {
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        BackHeader("导入单词", nav)
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                OutlinedTextField(name, { name = it }, label = { Text("批次名称") }, placeholder = { Text("例如：9 月 2 日英语作业") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(
                    text, { text = it; vm.preview(it) }, label = { Text("单词内容") },
                    placeholder = { Text("每行一个单词，也可以带释义\nachieve v. 实现；达到\nmaintain v. 保持；维持") },
                    minLines = 9, modifier = Modifier.fillMaxWidth(),
                )
            }
            preview?.let { value ->
                item { PreviewSummary(value) }
                items(value.lines.take(12)) { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(line.word ?: line.rawText, modifier = Modifier.weight(1f))
                        Text(
                            when (line.status.name) { "VALID" -> "有效"; "DUPLICATE" -> "重复"; "ERROR" -> "错误"; else -> "需检查" },
                            color = if (line.status.name == "ERROR") Coral else Sage,
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = { vm.import(name, text) { nav.popBackStack() } },
                    enabled = !busy && preview?.accepted?.isNotEmpty() == true && preview.errorCount == 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "正在导入…" else "确认导入", modifier = Modifier.padding(6.dp)) }
            }
        }
    }
}

@Composable private fun PreviewSummary(preview: ImportPreview) {
    Surface(shape = RoundedCornerShape(18.dp), color = SageSoft) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceAround) {
            MiniStat("有效", preview.accepted.size); MiniStat("重复", preview.duplicateCount); MiniStat("错误", preview.errorCount)
        }
    }
}

@Composable private fun MiniStat(label: String, value: Int) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value.toString(), fontWeight = FontWeight.Bold, fontSize = 20.sp); Text(label, style = MaterialTheme.typography.labelSmall) } }

@Composable
private fun SetupScreen(state: AppUiState, vm: AppViewModel, nav: NavHostController) {
    Column(Modifier.fillMaxSize()) {
        BackHeader("设置晨测", nav)
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { SectionTitle("选择批次", "同一单词会自动去重") }
            items(state.batches) { batch ->
                val selected = batch.id in state.selectedBatchIds
                Surface(
                    shape = RoundedCornerShape(18.dp), color = if (selected) SageSoft else Paper,
                    modifier = Modifier.fillMaxWidth().clickable { vm.toggleBatch(batch.id) },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(selected, { vm.toggleBatch(batch.id) })
                        Column(Modifier.padding(start = 8.dp)) { Text(batch.name, fontWeight = FontWeight.SemiBold); Text("${batch.wordCount} 个单词", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item {
                SectionTitle("测试方式", "两种方式共享同一份学习记录")
                Spacer(Modifier.height(10.dp))
                ModeCard(TestMode.STUDENT, state.selectedMode, "学生自测", "作答前隐藏中文释义", vm)
                Spacer(Modifier.height(8.dp))
                ModeCard(TestMode.PARENT, state.selectedMode, "家长考我", "直接展示完整答案", vm)
            }
            item {
                Button(
                    onClick = { vm.startDaily { nav.navigate("test/$it") } },
                    enabled = state.selectedBatchIds.isNotEmpty() && !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("开始", modifier = Modifier.padding(6.dp)) }
            }
        }
    }
}

@Composable private fun ModeCard(mode: TestMode, selected: TestMode, title: String, detail: String, vm: AppViewModel) {
    Surface(shape = RoundedCornerShape(18.dp), color = if (mode == selected) SageSoft else Paper, modifier = Modifier.fillMaxWidth().clickable { vm.selectMode(mode) }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(mode == selected, { vm.selectMode(mode) }); Column(Modifier.padding(start = 8.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(detail, style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = .58f)) }
        }
    }
}

@Composable
internal fun TestScreen(state: AppUiState, vm: AppViewModel, nav: NavHostController) {
    val session = state.session
    if (session == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    if (session.session.phase == TestPhase.ROUND_SUMMARY && state.feedbackCard == null) {
        RoundSummaryScreen(session, state.isBusy, vm, nav)
        return
    }
    val card = state.feedbackCard ?: session.current ?: return
    val answerShown = state.answerVisible || session.session.mode == TestMode.PARENT
    val context = LocalContext.current.applicationContext
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    var speechError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(context) {
        val engine = TextToSpeech(context) { status ->
            val active = tts
            if (status == TextToSpeech.SUCCESS && active != null) {
                val locale = when {
                    active.isLanguageAvailable(Locale.US) >= TextToSpeech.LANG_AVAILABLE -> Locale.US
                    active.isLanguageAvailable(Locale.ENGLISH) >= TextToSpeech.LANG_AVAILABLE -> Locale.ENGLISH
                    else -> null
                }
                ttsReady = locale != null && active.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE
                if (ttsReady) {
                    active.voices
                        ?.filter { voice -> voice.locale.language == Locale.ENGLISH.language }
                        ?.sortedWith(
                            compareByDescending<android.speech.tts.Voice> { it.locale.country == Locale.US.country }
                                .thenByDescending { it.quality }
                                .thenBy { it.latency }
                        )
                        ?.firstOrNull()
                        ?.let { active.voice = it }
                }
                if (!ttsReady) speechError = "设备缺少可用的英文语音，请在系统设置中安装文字转语音服务"
            } else {
                speechError = "文字转语音服务初始化失败，请检查系统语音设置"
            }
        }
        engine.setSpeechRate(0.72f)
        engine.setPitch(1.0f)
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = Unit
            @Deprecated("Deprecated by Android")
            override fun onError(utteranceId: String?) { speechError = "发音播放失败，请检查媒体音量和系统语音服务" }
        })
        tts = engine
        onDispose { engine.stop(); engine.shutdown(); tts = null }
    }
    fun speak() {
        if (!ttsReady) {
            speechError = "发音暂不可用，请检查媒体音量和系统文字转语音服务"
        } else if (tts?.speak(card.word, TextToSpeech.QUEUE_FLUSH, null, "word-${card.id}-${System.nanoTime()}") == TextToSpeech.ERROR) {
            speechError = "发音播放失败，请重试"
        }
    }
    LaunchedEffect(card.id, ttsReady, state.settings.autoPronounce) {
        if (ttsReady && state.settings.autoPronounce) speak()
    }
    LaunchedEffect(speechError) {
        speechError?.let { vm.showMessage(it); speechError = null }
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.abandon { nav.navigate("home") { popUpTo("home") { inclusive = true } } } }) { Icon(Icons.Outlined.Close, "放弃") }
            Column(Modifier.weight(1f)) {
                Text(phaseLabel(session.session.phase), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge, color = Sage)
                LinearProgressIndicator(progress = { session.roundTestedCount.toFloat() / session.roundTotalCount.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            }
            Text("${session.roundTestedCount}/${session.roundTotalCount}", style = MaterialTheme.typography.labelLarge)
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            LiveStat("已测", session.roundTestedCount, Ink)
            LiveStat("会", session.roundKnownCount, Sage)
            LiveStat("不会", session.roundWrongCount, Coral)
        }
        Spacer(Modifier.height(16.dp))
        AnimatedContent(card, label = "word-card", modifier = Modifier.weight(1f)) { animatedCard ->
            StudyWordCard(animatedCard, answerShown, state.settings.largeFont, ttsReady, ::speak)
        }
        Spacer(Modifier.height(16.dp))
        if (state.answerVisible && session.session.mode == TestMode.STUDENT) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { vm.confirmSelfAssessment(false) },
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f),
                ) { Text("我错了", modifier = Modifier.padding(7.dp), color = Coral) }
                Button(
                    onClick = { vm.confirmSelfAssessment(true) },
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f),
                ) { Text("我对了", modifier = Modifier.padding(7.dp)) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { vm.answer(TestResult.UNKNOWN) }, enabled = !state.isBusy, modifier = Modifier.weight(1f)) { Text("不会", modifier = Modifier.padding(7.dp), color = Coral) }
                Button(onClick = { vm.answer(TestResult.KNOW) }, enabled = !state.isBusy, modifier = Modifier.weight(1f)) { Text("会", modifier = Modifier.padding(7.dp)) }
            }
        }
        if (session.session.type == SessionType.WRONG_REVIEW && answerShown) {
            TextButton(onClick = { vm.answer(TestResult.MASTERED) }, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) { Text("会（移出错词库）") }
        }
    }
}

@Composable
private fun LiveStat(label: String, value: Int, color: Color) {
    Text("$label $value", color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun RoundSummaryScreen(session: SessionView, busy: Boolean, vm: AppViewModel, nav: NavHostController) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = RoundedCornerShape(50), color = SageSoft) {
            Icon(Icons.Outlined.Assessment, null, tint = Sage, modifier = Modifier.padding(18.dp).size(48.dp))
        }
        Text("本轮测试完成", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 22.dp))
        Text("第 ${session.session.roundNumber + 1} 轮测试统计", color = Ink.copy(alpha = .58f), modifier = Modifier.padding(top = 8.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = Paper, modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp)) {
            Row(Modifier.padding(22.dp), horizontalArrangement = Arrangement.SpaceAround) {
                MiniStat("已测试", session.roundTestedCount)
                MiniStat("会", session.roundKnownCount)
                MiniStat("错", session.roundWrongCount)
            }
        }
        Button(
            onClick = vm::retryWrongAnswers,
            enabled = session.roundWrongCount > 0 && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (session.roundWrongCount > 0) "错题重新测试（${session.roundWrongCount}）" else "本轮没有错题", modifier = Modifier.padding(7.dp)) }
        OutlinedButton(
            onClick = { vm.completeToday { nav.navigate("home") { popUpTo("home") { inclusive = true } } } },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) { Text("退出并完成今日测试", modifier = Modifier.padding(7.dp)) }
    }
}

@Composable
private fun CompletionScreen(session: SessionView?, nav: NavHostController) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = RoundedCornerShape(50), color = SageSoft) { Icon(Icons.Outlined.CheckCircle, null, tint = Sage, modifier = Modifier.padding(18.dp).size(48.dp)) }
        Text("今日完成", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 22.dp))
        Text("每一次认真回想，都在让记忆更牢。", color = Ink.copy(alpha = .58f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = Paper, modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp)) {
            Row(Modifier.padding(22.dp), horizontalArrangement = Arrangement.SpaceAround) {
                MiniStat("总词数", session?.session?.totalCount ?: 0)
                MiniStat("首轮会", session?.session?.firstPassCount ?: 0)
                MiniStat("首轮错", session?.session?.firstWrongCount ?: 0)
            }
        }
        Button(onClick = { nav.navigate("home") { popUpTo("home") { inclusive = true } } }, modifier = Modifier.fillMaxWidth()) { Text("回到今日", modifier = Modifier.padding(7.dp)) }
    }
}

@Composable
private fun WrongScreen(state: AppUiState, vm: AppViewModel, nav: NavHostController) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        PageHeader("错词", "保留每次错误，也看见每次进步")
        Surface(shape = RoundedCornerShape(24.dp), color = Coral, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("当前待复习", color = Color.White.copy(.75f)); Text("${state.wrongWords.size} 个", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold) }
                Button(onClick = { nav.navigate("wrong/review") }, enabled = state.wrongWords.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Coral)) { Text("开始复测") }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (state.wrongWords.isEmpty()) EmptyState(Icons.Outlined.CheckCircleOutline, "暂时没有错词", "保持这个状态，很棒。")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(state.wrongWordGroups, key = { it.batchId }) { group ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Paper,
                    modifier = Modifier.fillMaxWidth().clickable { nav.navigate("wrong/batch/${group.batchId}") },
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(14.dp), color = SageSoft) {
                            Icon(Icons.Outlined.Folder, null, tint = Sage, modifier = Modifier.padding(12.dp))
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(group.batchName, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                            Text("${group.words.size} 个错词", color = Coral, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Outlined.ChevronRight, "查看错词")
                    }
                }
            }
        }
    }
}

@Composable
private fun WrongBatchScreen(group: com.morningwords.data.repository.WrongWordGroup?, nav: NavHostController) {
    Column(Modifier.fillMaxSize()) {
        BackHeader(group?.batchName ?: "错词分类", nav)
        if (group == null) {
            EmptyState(Icons.Outlined.FolderOff, "这个词库已不存在", "返回错词页查看其他分类。")
        } else {
            Text("${group.words.size} 个错词", color = Coral, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), fontWeight = FontWeight.SemiBold)
            LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(group.words, key = { it.wrong.id }) { row ->
                    WrongRow(row) { nav.navigate("wrong/word/${row.word.id}") }
                }
            }
        }
    }
}

@Composable private fun WrongRow(row: WrongWordRow, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Paper, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                VocabularyHeading(row.word.word, VocabularyHeadingSize.LIST)
                row.word.meaning?.let { Text(it, color = Ink.copy(.58f)) }
            }
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFE5DE)) { Text("错 ${row.wrong.wrongCount + row.wrong.reviewWrongCount} 次", color = Coral, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium) }
            Icon(Icons.Outlined.ChevronRight, "查看详情", tint = Ink.copy(.45f), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun WrongWordDetailScreen(row: WrongWordRow?, nav: NavHostController) {
    Column(Modifier.fillMaxSize()) {
        BackHeader("单词详情", nav)
        if (row == null) {
            EmptyState(Icons.Outlined.SearchOff, "找不到这个错词", "它可能已经移出错词库。")
        } else {
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Surface(shape = RoundedCornerShape(26.dp), color = Paper, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            VocabularyHeading(row.word.word, VocabularyHeadingSize.DETAIL)
                            row.word.phonetic?.let { Text(it, color = Ink.copy(.5f), modifier = Modifier.padding(top = 5.dp)) }
                            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFE5DE), modifier = Modifier.padding(top = 16.dp)) {
                                Text("累计答错 ${row.wrong.wrongCount + row.wrong.reviewWrongCount} 次", color = Coral, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                            }
                        }
                    }
                }
                item { DetailItem("词性", row.word.partOfSpeech ?: "暂无词性") }
                item { DetailItem("释义", row.word.meaning ?: "暂无释义") }
                item { DetailItem("例句", row.word.example ?: "暂无例句", row.word.word) }
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String, highlightWord: String? = null) {
    Surface(shape = RoundedCornerShape(18.dp), color = Paper, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(label, color = Sage, style = MaterialTheme.typography.labelLarge)
            Text(
                highlightWord?.let { highlightedExample(value, it) } ?: AnnotatedString(value),
                modifier = Modifier.padding(top = 7.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

internal fun highlightedExample(example: String, word: String): AnnotatedString {
    val matches = findExampleWordRanges(example, word)
    if (matches.isEmpty()) return AnnotatedString(example)
    return buildAnnotatedString {
        var cursor = 0
        matches.forEach { range ->
            append(example.substring(cursor, range.first))
            withStyle(SpanStyle(color = ExampleWordRed, fontWeight = FontWeight.Bold)) {
                append(example.substring(range.first, range.last + 1))
            }
            cursor = range.last + 1
        }
        append(example.substring(cursor))
    }
}

internal fun findExampleWordRanges(example: String, word: String): List<IntRange> {
    val base = word.trim()
    if (base.isEmpty()) return emptyList()
    val variants = linkedSetOf(base, "${base}s", "${base}es", "${base}d", "${base}ed", "${base}ing")
    if (base.endsWith("e", ignoreCase = true) && base.length > 1) variants += base.dropLast(1) + "ing"
    if (base.endsWith("y", ignoreCase = true) && base.length > 1) {
        variants += base.dropLast(1) + "ies"
        variants += base.dropLast(1) + "ied"
    }
    if (base.length >= 3 && base.last().lowercaseChar() !in "aeiouwxy" && base[base.lastIndex - 1].lowercaseChar() in "aeiou") {
        variants += base + base.last() + "ed"
        variants += base + base.last() + "ing"
    }
    val alternatives = variants.sortedByDescending(String::length).joinToString("|") { Regex.escape(it) }
    return Regex("(?<![A-Za-z])(?:$alternatives)(?![A-Za-z])", RegexOption.IGNORE_CASE)
        .findAll(example)
        .map { it.range }
        .toList()
}

@Composable
private fun WrongReviewSetup(state: AppUiState, vm: AppViewModel, nav: NavHostController) {
    var selectedBatchIds by remember { mutableStateOf(emptySet<Long>()) }
    var selectionInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(state.wrongWordGroups) {
        val available = state.wrongWordGroups.mapTo(mutableSetOf()) { it.batchId }
        if (!selectionInitialized && available.isNotEmpty()) {
            selectedBatchIds = available
            selectionInitialized = true
        } else {
            selectedBatchIds = selectedBatchIds intersect available
        }
    }
    Column(Modifier.fillMaxSize()) {
        BackHeader("错词复测", nav)
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("选择复测文件夹", "复测所选词库中全部待复习错词")
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { selectedBatchIds = state.wrongWordGroups.mapTo(mutableSetOf()) { it.batchId } }) { Text("全选") }
                    TextButton(onClick = { selectedBatchIds = emptySet() }) { Text("清空") }
                }
            }
            items(state.wrongWordGroups, key = { "review-folder-${it.batchId}" }) { group ->
                val selected = group.batchId in selectedBatchIds
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) SageSoft else Paper,
                    modifier = Modifier.fillMaxWidth().clickable {
                        selectedBatchIds = if (selected) selectedBatchIds - group.batchId else selectedBatchIds + group.batchId
                    },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { checked -> selectedBatchIds = if (checked) selectedBatchIds + group.batchId else selectedBatchIds - group.batchId },
                        )
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(group.batchName, fontWeight = FontWeight.SemiBold)
                            Text("${group.words.size} 个错词", color = Coral, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                SectionTitle("测试方式", "学生自测隐藏答案，家长考我直接显示答案")
                Spacer(Modifier.height(10.dp))
                ModeCard(TestMode.STUDENT, state.selectedMode, "学生自测", "作答前隐藏中文释义", vm)
                Spacer(Modifier.height(8.dp))
                ModeCard(TestMode.PARENT, state.selectedMode, "家长考我", "直接展示完整答案", vm)
            }
            item { Text("复测中可以选择“会（移出错词库）”；所有历史记录仍会保留。", color = Ink.copy(.58f), style = MaterialTheme.typography.bodySmall) }
            item {
                Button(
                    onClick = { vm.startWrongReview(selectedBatchIds) { nav.navigate("test/$it") } },
                    enabled = selectedBatchIds.isNotEmpty() && !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (selectedBatchIds.isEmpty()) "请先选择复测文件夹" else "开始复测", modifier = Modifier.padding(7.dp)) }
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: AppUiState, vm: AppViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("我的", "让晨测更适合你的习惯") }
        item {
            SettingsGroup("默认测试方式") {
                SettingsChoice("学生自测", state.settings.defaultTestMode == TestMode.STUDENT) { vm.setMode(TestMode.STUDENT) }
                HorizontalDivider(); SettingsChoice("家长考我", state.settings.defaultTestMode == TestMode.PARENT) { vm.setMode(TestMode.PARENT) }
            }
        }
        item {
            SettingsGroup("测试体验") {
                SettingsSwitch("自动朗读英文", state.settings.autoPronounce, vm::setAutoPronounce)
                HorizontalDivider(); SettingsSwitch("大号单词字体", state.settings.largeFont, vm::setLargeFont)
            }
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = SageSoft) {
                Row(Modifier.padding(16.dp)) { Icon(Icons.Outlined.CloudOff, null, tint = Sage); Text("所有单词和学习记录只保存在本机，核心功能无需联网。", modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall) }
            }
        }
        item { Text("晨词 · V1.0", modifier = Modifier.fillMaxWidth().padding(18.dp), textAlign = TextAlign.Center, color = Ink.copy(.38f), style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column { Text(title, style = MaterialTheme.typography.labelLarge, color = Sage, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)); Surface(shape = RoundedCornerShape(20.dp), color = Paper) { Column(content = content) } }
}
@Composable private fun SettingsChoice(label: String, selected: Boolean, action: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); RadioButton(selected, action) } }
@Composable private fun SettingsSwitch(label: String, checked: Boolean, action: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, action) } }

@Composable private fun PageHeader(title: String, subtitle: String) { Column(Modifier.padding(vertical = 20.dp)) { Text(title, style = MaterialTheme.typography.headlineLarge); Text(subtitle, color = Ink.copy(.55f), modifier = Modifier.padding(top = 4.dp)) } }
@Composable private fun BackHeader(title: String, nav: NavHostController) { TopAppBar(title = { Text(title, fontWeight = FontWeight.SemiBold) }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)) }
@Composable private fun SectionTitle(title: String, subtitle: String) { Column { Text(title, style = MaterialTheme.typography.titleLarge); Text(subtitle, color = Ink.copy(.5f), style = MaterialTheme.typography.bodySmall) } }
@Composable private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) { Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = Sage.copy(.55f), modifier = Modifier.size(44.dp)); Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp)); Text(detail, color = Ink.copy(.48f), modifier = Modifier.padding(top = 4.dp)) } }
private fun phaseLabel(phase: TestPhase) = when (phase) { TestPhase.FIRST_ROUND -> "第一轮"; TestPhase.WRONG_LOOP -> "错题重新测试"; TestPhase.FINAL_CHECK -> "错题重新测试"; TestPhase.WRONG_REVIEW -> "错词复测"; TestPhase.ROUND_SUMMARY -> "本轮统计"; TestPhase.COMPLETED -> "已完成" }
