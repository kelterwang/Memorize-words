from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
DESIGN_DOCUMENT = ROOT / "设计文档.md"


class DesignDocumentTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.content = DESIGN_DOCUMENT.read_text(encoding="utf-8")

    def test_required_sections_are_present(self) -> None:
        required_sections = [
            "总体架构",
            "Room 数据设计",
            "正常晨测状态机",
            "错词复测状态机",
            "作答事务设计",
            "自动保存、恢复与放弃",
            "导入与批次管理",
            "测试设计",
            "验收场景追踪",
        ]
        for section in required_sections:
            with self.subTest(section=section):
                self.assertRegex(
                    self.content, rf"(?m)^#{{2,3}} .*{re.escape(section)}$", section
                )

    def test_fixed_enum_values_are_documented(self) -> None:
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
        for enum_name, values in enum_values.items():
            with self.subTest(enum_name=enum_name):
                declaration = re.search(
                    rf"enum class {enum_name} \{{(?P<body>[^}}]+)\}}", self.content
                )
                self.assertIsNotNone(declaration, enum_name)
                for value in values:
                    self.assertIn(value, declaration.group("body"))

    def test_all_acceptance_criteria_are_traceable(self) -> None:
        for number in range(1, 15):
            acceptance_id = f"AC-{number:02d}"
            with self.subTest(acceptance_id=acceptance_id):
                self.assertEqual(self.content.count(f"| {acceptance_id} |"), 1)

    def test_required_state_machine_paths_are_documented(self) -> None:
        paths = [
            "FIRST_ROUND → COMPLETED",
            "FIRST_ROUND → WRONG_LOOP → FINAL_CHECK → COMPLETED",
            (
                "FIRST_ROUND → WRONG_LOOP → FINAL_CHECK → WRONG_LOOP → "
                "FINAL_CHECK → COMPLETED"
            ),
        ]
        for path in paths:
            with self.subTest(path=path):
                self.assertIn(path, self.content)

    def test_required_ui_wording_is_documented(self) -> None:
        for wording in ["会", "不会", "会（移出错词库）", "继续晨测", "今日完成"]:
            with self.subTest(wording=wording):
                self.assertIn(wording, self.content)


if __name__ == "__main__":
    unittest.main()
