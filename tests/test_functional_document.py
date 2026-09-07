"""Guard the functional inventory against navigation and theme drift."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]


class FunctionalDocumentTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.doc = (ROOT / "功能说明文档.md").read_text()
        cls.ui = (ROOT / "app/src/main/java/com/morningwords/ui/MorningWordsApp.kt").read_text()

    def test_registered_routes_have_inventory_entries(self):
        routes = set(re.findall(r'composable\("([^"]+)"\)', self.ui))
        documented = set(re.findall(r'\| `([^`]+)` \|', self.doc))
        self.assertTrue(routes)
        self.assertEqual(routes, documented)

    def test_documented_source_links_resolve(self):
        links = re.findall(r'\]\(([^)]+)\)', self.doc)
        self.assertTrue(links)
        for link in links:
            with self.subTest(link=link):
                self.assertTrue((ROOT / link).is_file())

    def test_named_theme_colors_match_code(self):
        for name in ("Ink", "Cream", "Paper", "Sage", "SageSoft", "Coral", "Gold", "ExampleWordRed"):
            with self.subTest(color=name):
                color = re.search(rf'val {name} = Color\(0xFF([0-9A-F]{{6}})\)', self.ui)
                self.assertIsNotNone(color)
                self.assertIn("#" + color.group(1), self.doc)


if __name__ == "__main__":
    unittest.main()
