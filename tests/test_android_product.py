from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]


class AndroidProductStructureTest(unittest.TestCase):
    def test_android_project_entry_points_exist(self) -> None:
        paths = [
            "settings.gradle.kts",
            "README.md",
            "gradle/libs.versions.toml",
            "app/build.gradle.kts",
            "app/src/main/AndroidManifest.xml",
            "app/src/main/java/com/morningwords/MainActivity.kt",
            "app/src/main/java/com/morningwords/data/local/AppDatabase.kt",
            "app/src/main/java/com/morningwords/ui/MorningWordsApp.kt",
        ]
        for path in paths:
            with self.subTest(path=path):
                self.assertTrue((ROOT / path).is_file())

    def test_fixed_enums_match_product_baseline(self) -> None:
        source = (ROOT / "app/src/main/java/com/morningwords/domain/model/Models.kt").read_text(encoding="utf-8")
        expected = {
            "SessionType": ["DAILY_TEST", "WRONG_REVIEW"],
            "TestMode": ["STUDENT", "PARENT"],
            "SessionStatus": ["IN_PROGRESS", "COMPLETED", "ABANDONED"],
            "TestPhase": ["FIRST_ROUND", "WRONG_LOOP", "FINAL_CHECK", "WRONG_REVIEW", "ROUND_SUMMARY", "COMPLETED"],
            "TestResult": ["KNOW", "UNKNOWN", "MASTERED"],
        }
        for name, values in expected.items():
            declaration = re.search(rf"enum class {name} \{{([^}}]+)\}}", source)
            self.assertIsNotNone(declaration)
            self.assertEqual(values, [item.strip() for item in declaration.group(1).split(",")])

    def test_all_room_tables_are_declared(self) -> None:
        source = (ROOT / "app/src/main/java/com/morningwords/data/entity/Entities.kt").read_text(encoding="utf-8")
        for table in ["Word", "WordBatch", "BatchWord", "TestSession", "SessionBatch", "SessionWord", "TestRecord", "WrongWord", "WrongRecord", "UndoSnapshot"]:
            self.assertIn(f'tableName = "{table}"', source)

    def test_primary_product_routes_are_present(self) -> None:
        source = (ROOT / "app/src/main/java/com/morningwords/ui/MorningWordsApp.kt").read_text(encoding="utf-8")
        for route in ["home", "library", "batch/{batchId}", "import", "setup", "test/{sessionId}", "completed", "wrong", "wrong/batch/{batchId}", "wrong/word/{wordId}", "wrong/review", "settings"]:
            self.assertIn(f'composable("{route}")', source)

    def test_library_folders_open_ordered_word_details(self) -> None:
        repository = (ROOT / "app/src/main/java/com/morningwords/data/repository/MorningWordsRepository.kt").read_text(encoding="utf-8")
        dao = (ROOT / "app/src/main/java/com/morningwords/data/dao/MorningWordsDao.kt").read_text(encoding="utf-8")
        ui = (ROOT / "app/src/main/java/com/morningwords/ui/MorningWordsApp.kt").read_text(encoding="utf-8")
        self.assertIn("suspend fun wordsInBatch(batchId: Long)", repository)
        self.assertIn("WHERE bw.batchId=:batchId ORDER BY bw.sortOrder", dao)
        self.assertIn('nav.navigate("batch/${batch.id}")', ui)
        self.assertIn("private fun BatchDetailScreen", ui)
        self.assertIn('Text("点击单词查看完整词性、释义和例句"', ui)
        self.assertIn("highlightedExample(it, word.word)", ui)

    def test_known_answer_requires_correct_or_wrong_self_assessment(self) -> None:
        source = (ROOT / "app/src/main/java/com/morningwords/ui/MorningWordsApp.kt").read_text(encoding="utf-8")
        confirmation = re.search(
            r"if \(state\.answerVisible.*?\}\s*else \{",
            source,
            re.DOTALL,
        )
        self.assertIsNotNone(confirmation)
        self.assertIn("vm.confirmSelfAssessment(false)", confirmation.group(0))
        self.assertIn('Text("我错了"', confirmation.group(0))
        self.assertIn("vm.confirmSelfAssessment(true)", confirmation.group(0))
        self.assertIn('Text("我对了"', confirmation.group(0))

        view_model = (ROOT / "app/src/main/java/com/morningwords/ui/AppViewModel.kt").read_text(encoding="utf-8")
        self.assertIn("needsStudentAnswerConfirmation(session.session.mode, result)", view_model)
        self.assertIn("submitAnswer(if (isCorrect) TestResult.KNOW else TestResult.UNKNOWN)", view_model)

    def test_pronunciation_uses_clear_speech_configuration(self) -> None:
        source = (ROOT / "app/src/main/java/com/morningwords/ui/MorningWordsApp.kt").read_text(encoding="utf-8")
        for configuration in [
            "it.locale.country == Locale.US.country",
            ".thenByDescending { it.quality }",
            "engine.setSpeechRate(0.72f)",
            "AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY",
            "AudioAttributes.CONTENT_TYPE_SPEECH",
        ]:
            with self.subTest(configuration=configuration):
                self.assertIn(configuration, source)

    def test_wrong_review_offers_student_and_parent_modes(self) -> None:
        source = (ROOT / "app/src/main/java/com/morningwords/ui/MorningWordsApp.kt").read_text(encoding="utf-8")
        screen = re.search(r"private fun WrongReviewSetup\(.*?\n\}\n\n@Composable", source, re.DOTALL)
        self.assertIsNotNone(screen)
        self.assertIn('ModeCard(TestMode.STUDENT', screen.group(0))
        self.assertIn('ModeCard(TestMode.PARENT', screen.group(0))
        self.assertIn('SectionTitle("测试方式"', screen.group(0))

    def test_wrong_review_can_filter_by_selected_library_folders(self) -> None:
        dao = (ROOT / "app/src/main/java/com/morningwords/data/dao/MorningWordsDao.kt").read_text(encoding="utf-8")
        ui = (ROOT / "app/src/main/java/com/morningwords/ui/MorningWordsApp.kt").read_text(encoding="utf-8")
        self.assertIn("bw.batchId IN (:batchIds)", dao)
        self.assertIn('SectionTitle("选择复测文件夹"', ui)
        self.assertIn("selectedBatchIds.isNotEmpty() && !state.isBusy", ui)
        self.assertIn('Text("全选")', ui)
        self.assertIn('Text("清空")', ui)

    def test_library_rename_is_shared_with_wrong_word_categories(self) -> None:
        dao = (ROOT / "app/src/main/java/com/morningwords/data/dao/MorningWordsDao.kt").read_text(encoding="utf-8")
        ui = (ROOT / "app/src/main/java/com/morningwords/ui/MorningWordsApp.kt").read_text(encoding="utf-8")
        self.assertIn("UPDATE WordBatch SET batchName=:name", dao)
        self.assertIn("b.batchName AS batchName", dao)
        self.assertIn('Text("导入时间 ${formatImportTime(batch.createdAt)}"', ui)
        self.assertIn('Icon(Icons.Outlined.Edit, "修改名称")', ui)

    def test_import_separates_and_repairs_word_details(self) -> None:
        importer = (ROOT / "app/src/main/java/com/morningwords/domain/importer/WordImporter.kt").read_text(encoding="utf-8")
        repository = (ROOT / "app/src/main/java/com/morningwords/data/repository/MorningWordsRepository.kt").read_text(encoding="utf-8")
        view_model = (ROOT / "app/src/main/java/com/morningwords/ui/AppViewModel.kt").read_text(encoding="utf-8")
        self.assertIn("val example: String? = null", importer)
        self.assertIn("splitMeaningAndExample", importer)
        self.assertIn("example = line.example", repository)
        self.assertIn("repairImportedWordFields", repository)
        self.assertIn("repository.repairImportedWordFields()", view_model)

    def test_examples_highlight_the_current_word(self) -> None:
        ui = (ROOT / "app/src/main/java/com/morningwords/ui/MorningWordsApp.kt").read_text(encoding="utf-8")
        self.assertGreaterEqual(ui.count("highlightedExample("), 3)
        self.assertIn("ExampleWordRed", ui)
        self.assertIn("findExampleWordRanges", ui)

    def test_home_uses_time_greeting_and_persisted_non_repeating_quote(self) -> None:
        motivation = (ROOT / "app/src/main/java/com/morningwords/domain/motivation/DailyMotivation.kt").read_text(encoding="utf-8")
        settings = (ROOT / "app/src/main/java/com/morningwords/data/settings/SettingsRepository.kt").read_text(encoding="utf-8")
        ui = (ROOT / "app/src/main/java/com/morningwords/ui/MorningWordsApp.kt").read_text(encoding="utf-8")
        self.assertIn("fun greetingForHour(hour: Int)", motivation)
        self.assertIn("fun selectDailyQuote(", motivation)
        self.assertEqual(31, len(re.findall(r'^\s+".+",$', motivation, re.MULTILINE)))
        self.assertIn('stringPreferencesKey("dailyQuoteDate")', settings)
        self.assertIn('stringPreferencesKey("dailyQuoteRemaining")', settings)
        self.assertIn("Text(state.greeting", ui)
        self.assertIn("Text(state.dailyQuote", ui)

    def test_home_uses_generic_test_wording(self) -> None:
        ui = (ROOT / "app/src/main/java/com/morningwords/ui/MorningWordsApp.kt").read_text(encoding="utf-8")
        self.assertIn('else "开始测试"', ui)
        self.assertIn('SectionTitle("测试节奏"', ui)
        self.assertNotIn('else "开始晨测"', ui)
        self.assertNotIn('SectionTitle("晨测节奏"', ui)


if __name__ == "__main__":
    unittest.main()
