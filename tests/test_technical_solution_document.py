from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
TECHNICAL_SOLUTION = ROOT / "技术方案文档.md"


class TechnicalSolutionDocumentTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.content = TECHNICAL_SOLUTION.read_text(encoding="utf-8")

    def test_implementation_sections_are_present(self) -> None:
        sections = [
            "工程与构建方案",
            "Room 实现方案",
            "DAO 与查询契约",
            "Repository 契约",
            "纯 Kotlin 状态机",
            "原子事务流程",
            "导入实现",
            "ViewModel 与 UI 实现",
            "测试实施方案",
            "Phase 实施清单",
            "Definition of Done",
        ]
        for section in sections:
            with self.subTest(section=section):
                self.assertRegex(
                    self.content, rf"(?m)^## .*{re.escape(section)}$", section
                )

    def test_fixed_enum_values_match_the_design(self) -> None:
        enum_values = {
            "SessionType": ["DAILY_TEST", "WRONG_REVIEW"],
            "TestMode": ["STUDENT", "PARENT"],
            "SessionStatus": ["IN_PROGRESS", "COMPLETED", "ABANDONED"],
            "TestPhase": [
                "FIRST_ROUND",
                "WRONG_LOOP",
                "FINAL_CHECK",
                "WRONG_REVIEW",
                "COMPLETED",
            ],
            "TestResult": ["KNOW", "UNKNOWN", "MASTERED"],
            "WrongWordStatus": ["ACTIVE", "MASTERED"],
            "WrongRecordType": ["FIRST_ROUND_WRONG", "WRONG_REVIEW_WRONG"],
        }
        for name, expected_values in enum_values.items():
            declaration = re.search(
                rf"enum class {name} \{{(?P<body>[^}}]+)\}}", self.content
            )
            with self.subTest(enum=name):
                self.assertIsNotNone(declaration)
                actual_values = [
                    value.strip() for value in declaration.group("body").split(",")
                ]
                self.assertEqual(actual_values, expected_values)

    def test_all_persistence_tables_are_covered(self) -> None:
        tables = [
            "Word",
            "WordBatch",
            "BatchWord",
            "TestSession",
            "SessionBatch",
            "SessionWord",
            "TestRecord",
            "WrongWord",
            "WrongRecord",
            "UndoSnapshot",
        ]
        for table in tables:
            with self.subTest(table=table):
                self.assertIn(f"| `{table}Entity` | `{table}` |", self.content)

    def test_required_transaction_entry_points_are_covered(self) -> None:
        methods = [
            "importBatch",
            "createDailySession",
            "createWrongReviewSession",
            "submitAnswer",
            "undoLastAnswer",
            "abandonSession",
            "deleteBatchSafely",
        ]
        for method in methods:
            with self.subTest(method=method):
                self.assertRegex(self.content, rf"suspend fun {method}\(")

    def test_undo_snapshot_distinguishes_new_wrong_records(self) -> None:
        self.assertIn("wrongRecordCreated: Boolean", self.content)
        self.assertIn("仅当 `wrongRecordCreated=true` 时删除", self.content)

    def test_wrong_review_range_query_groups_by_word(self) -> None:
        self.assertIn("GROUP BY wr.wordId", self.content)
        self.assertIn("ORDER BY MAX(wr.wrongAt) DESC", self.content)

    def test_acceptance_criteria_have_exactly_one_mapping(self) -> None:
        for number in range(1, 15):
            acceptance_id = f"AC-{number:02d}"
            with self.subTest(acceptance_id=acceptance_id):
                self.assertEqual(self.content.count(f"| {acceptance_id} |"), 1)

    def test_all_six_phases_are_actionable(self) -> None:
        for number in range(1, 7):
            with self.subTest(phase=number):
                self.assertRegex(self.content, rf"(?m)^### Phase {number}：")

    def test_standard_verification_commands_are_documented(self) -> None:
        commands = [
            "./gradlew testDebugUnitTest",
            "./gradlew lintDebug",
            "./gradlew assembleDebug",
            "./gradlew connectedDebugAndroidTest",
            "python3 -m unittest discover -s tests -v",
            "git diff --check",
        ]
        for command in commands:
            with self.subTest(command=command):
                self.assertIn(command, self.content)


if __name__ == "__main__":
    unittest.main()
