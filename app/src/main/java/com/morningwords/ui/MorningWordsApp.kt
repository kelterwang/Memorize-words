@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.morningwords.ui

import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import java.util.Locale

private val Ink = Color(0xFF292621)
private val Cream = Color(0xFFFFF9F1)
private val Paper = Color(0xFFFFFDF9)
private val Sage = Color(0xFF54715A)
private val SageSoft = Color(0xFFDDE8DA)
private val Coral = Color(0xFFB95F4B)
private val Gold = Color(0xFFE7B75D)

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
                composable("import") { ImportScreen(state.importPreview, state.isBusy, viewModel, nav) }
                composable("setup") { SetupScreen(state, viewModel, nav) }
                composable("test/{sessionId}") { entry ->
                    val id = entry.arguments?.getString("sessionId")?.toLongOrNull() ?: return@composable
                    LaunchedEffect(id) { viewModel.loadSession(id) }
                    TestScreen(state, viewModel, nav)
                }
                composable("completed") { CompletionScreen(state.session, nav) }
                composable("wrong") { WrongScreen(state, viewModel, nav) }
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text("早上好", style = MaterialTheme.typography.labelLarge, color = Sage)
            Text("今天，也把昨天\n学过的词记牢。", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp))
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
                    ) { Text(if (state.activeSessionId != null) "继续晨测" else if (state.dashboard.wordCount == 0) "导入单词" else "开始晨测", modifier = Modifier.padding(6.dp)) }
                }
            }
        }
        item {
            SectionTitle("晨测节奏", "离线保存 · 每题即时记录")
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepChip("01", "首轮")
                StepChip("02", "错词循环")
                StepChip("03", "完整终验")
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
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        PageHeader("词库", "按老师布置的内容分批管理")
        Button(onClick = { nav.navigate("import") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("导入新批次")
        }
        Spacer(Modifier.height(14.dp))
        if (state.batches.isEmpty()) EmptyState(Icons.AutoMirrored.Outlined.MenuBook, "还没有批次", "从一行一个单词开始吧。")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(state.batches, key = { it.id }) { batch ->
                Surface(shape = RoundedCornerShape(20.dp), color = Paper) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(14.dp), color = SageSoft) {
                            Icon(Icons.Outlined.Folder, null, tint = Sage, modifier = Modifier.padding(12.dp))
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(batch.name, fontWeight = FontWeight.SemiBold)
                            Text("${batch.wordCount} 个单词", style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = .55f))
                        }
                        IconButton(onClick = { vm.deleteBatch(batch.id) }) { Icon(Icons.Outlined.DeleteOutline, "删除") }
                    }
                }
            }
        }
    }
}

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
private fun TestScreen(state: AppUiState, vm: AppViewModel, nav: NavHostController) {
    val session = state.session
    LaunchedEffect(session?.session?.status) { if (session?.session?.status == SessionStatus.COMPLETED) nav.navigate("completed") { popUpTo("home") } }
    if (session == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    val card = state.feedbackCard ?: session.current ?: return
    val answerShown = state.answerVisible || session.session.mode == TestMode.PARENT
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.abandon { nav.navigate("home") { popUpTo("home") { inclusive = true } } } }) { Icon(Icons.Outlined.Close, "放弃") }
            Column(Modifier.weight(1f)) {
                Text(phaseLabel(session.session.phase), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge, color = Sage)
                LinearProgressIndicator(progress = { session.passedCount.toFloat() / session.session.totalCount.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            }
            Text("${session.passedCount}/${session.session.totalCount}", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(28.dp))
        AnimatedContent(card, label = "word-card", modifier = Modifier.weight(1f)) { animatedCard ->
            Surface(shape = RoundedCornerShape(30.dp), color = Paper, shadowElevation = 2.dp, modifier = Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(animatedCard.word, fontSize = if (state.settings.largeFont) 48.sp else 42.sp, fontWeight = FontWeight.Bold, color = Ink)
                    animatedCard.phonetic?.let { Text(it, color = Ink.copy(alpha = .5f), modifier = Modifier.padding(top = 4.dp)) }
                    SpeakButton(animatedCard.word, state.settings.autoPronounce)
                    if (answerShown) {
                        HorizontalDivider(Modifier.padding(vertical = 22.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .45f))
                        animatedCard.partOfSpeech?.let { Text(it, color = Sage, fontWeight = FontWeight.Bold) }
                        animatedCard.requiredMeaning?.let { Text(it, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)) }
                        if (animatedCard.meaning != animatedCard.requiredMeaning) animatedCard.meaning?.let { Text(it, textAlign = TextAlign.Center, color = Ink.copy(alpha = .7f), modifier = Modifier.padding(top = 8.dp)) }
                        animatedCard.example?.let { Text(it, textAlign = TextAlign.Center, color = Ink.copy(alpha = .6f), modifier = Modifier.padding(top = 12.dp)) }
                    } else Text("想好后再作答", color = Ink.copy(alpha = .38f), modifier = Modifier.padding(top = 28.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (state.answerVisible && session.session.mode == TestMode.STUDENT) {
            Button(onClick = vm::continueAfterAnswer, modifier = Modifier.fillMaxWidth()) { Text("继续晨测", modifier = Modifier.padding(7.dp)) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { vm.answer(TestResult.UNKNOWN) }, enabled = !state.isBusy, modifier = Modifier.weight(1f)) { Text("不会", modifier = Modifier.padding(7.dp), color = Coral) }
                Button(onClick = { vm.answer(TestResult.KNOW) }, enabled = !state.isBusy, modifier = Modifier.weight(1f)) { Text("会", modifier = Modifier.padding(7.dp)) }
            }
            if (session.session.type == SessionType.WRONG_REVIEW) {
                TextButton(onClick = { vm.answer(TestResult.MASTERED) }, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) { Text("会（移出错词库）") }
            }
        }
    }
}

@Composable private fun SpeakButton(word: String, auto: Boolean) {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ready by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val engine = TextToSpeech(context) { status -> ready = status == TextToSpeech.SUCCESS }
        tts = engine
        onDispose { engine.stop(); engine.shutdown() }
    }
    LaunchedEffect(word, ready, auto) { if (ready && auto) { tts?.language = Locale.US; tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "auto-$word") } }
    IconButton(onClick = { if (ready) tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "manual-$word") }) { Icon(Icons.AutoMirrored.Outlined.VolumeUp, "发音", tint = if (ready) Sage else Color.Gray) }
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
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(state.wrongWords, key = { it.wrong.id }) { WrongRow(it) }
        }
    }
}

@Composable private fun WrongRow(row: WrongWordRow) {
    Surface(shape = RoundedCornerShape(18.dp), color = Paper) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(row.word.word, fontSize = 19.sp, fontWeight = FontWeight.SemiBold); row.word.meaning?.let { Text(it, color = Ink.copy(.58f)) } }
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFE5DE)) { Text("错 ${row.wrong.wrongCount + row.wrong.reviewWrongCount} 次", color = Coral, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun WrongReviewSetup(state: AppUiState, vm: AppViewModel, nav: NavHostController) {
    var days by remember { mutableIntStateOf(30) }
    Column(Modifier.fillMaxSize()) {
        BackHeader("错词复测", nav)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("选择错误发生时间", style = MaterialTheme.typography.titleLarge)
            listOf(1 to "最近 1 天", 30 to "最近 1 个月", 90 to "最近 3 个月").forEach { (value, label) ->
                Surface(shape = RoundedCornerShape(18.dp), color = if (days == value) SageSoft else Paper, modifier = Modifier.fillMaxWidth().clickable { days = value }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(days == value, { days = value }); Text(label, modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold) }
                }
            }
            Text("复测中可以选择“会（移出错词库）”；所有历史记录仍会保留。", color = Ink.copy(.58f), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { vm.startWrongReview(days) { nav.navigate("test/$it") } }, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) { Text("开始复测", modifier = Modifier.padding(7.dp)) }
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
                HorizontalDivider(); SettingsSwitch("答“会”后展示答案", state.settings.showAnswerAfterKnow, vm::setShowAnswer)
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
private fun phaseLabel(phase: TestPhase) = when (phase) { TestPhase.FIRST_ROUND -> "第一轮"; TestPhase.WRONG_LOOP -> "错词循环"; TestPhase.FINAL_CHECK -> "最终检验"; TestPhase.WRONG_REVIEW -> "错词复测"; TestPhase.COMPLETED -> "已完成" }
