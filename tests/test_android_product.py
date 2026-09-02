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
        for route in ["home", "library", "import", "setup", "test/{sessionId}", "completed", "wrong", "wrong/batch/{batchId}", "wrong/word/{wordId}", "wrong/review", "settings"]:
            self.assertIn(f'composable("{route}")', source)

    def test_known_answer_confirmation_stays_in_right_action_slot(self) -> None:
        source = (ROOT / "app/src/main/java/com/morningwords/ui/MorningWordsApp.kt").read_text(encoding="utf-8")
        confirmation = re.search(
            r"if \(state\.answerVisible.*?\}\s*else \{",
            source,
            re.DOTALL,
        )
        self.assertIsNotNone(confirmation)
        self.assertIn("Spacer(Modifier.weight(1f))", confirmation.group(0))
        self.assertIn(
            "Button(onClick = vm::continueAfterAnswer, modifier = Modifier.weight(1f))",
            confirmation.group(0),
        )

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


if __name__ == "__main__":
    unittest.main()
