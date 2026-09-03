from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]


class RestartSupportTest(unittest.TestCase):
    def run_mock_start(self, avd="MorningWords_API_36", installed=True):
        with tempfile.TemporaryDirectory(prefix="qiao restart ") as directory:
            root = Path(directory)
            (root / "scripts").mkdir()
            shutil.copy(ROOT / "scripts/android-env.sh", root / "scripts/android-env.sh")
            shutil.copy(ROOT / "启动淇澳背单词.command", root / "启动淇澳背单词.command")
            fixtures = {
                ".local-runtime/jdk/jdk-17.0.20.1+1/Contents/Home/bin/java": "#!/bin/bash\nexit 0\n",
                ".local-runtime/sdk/emulator/emulator": "#!/bin/bash\necho MorningWords_API_36\n",
                ".local-runtime/sdk/platform-tools/adb": '''#!/bin/bash
echo "$*" >> "$QIAO_TEST_LOG"
case "$*" in
    devices) printf 'List of devices attached\\nemulator-5554\\tdevice\\n' ;;
    *'emu avd name') printf '%s\\nOK\\n' "$QIAO_TEST_AVD" ;;
    *'getprop sys.boot_completed') echo 1 ;;
    *'pm path com.morningwords')
        if [[ "$QIAO_TEST_INSTALLED" == 1 ]]; then echo package:/data/app/test.apk; fi ;;
esac
exit 0
''',
                "安装包/淇澳背单词-1.0.apk": "test-fixture",
            }
            for name, content in fixtures.items():
                target = root / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
                target.chmod(0o755)
            log = root / "calls.log"
            env = dict(os.environ, QIAO_TEST_LOG=str(log), QIAO_TEST_AVD=avd,
                       QIAO_TEST_INSTALLED="1" if installed else "0")
            result = subprocess.run(["bash", str(root / "启动淇澳背单词.command")],
                                    cwd="/", env=env, capture_output=True, text=True, timeout=10)
            return result, log.read_text()

    def test_existing_installation_is_opened_without_reinstall(self) -> None:
        result, calls = self.run_mock_start()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("am start -W -n com.morningwords/.MainActivity", calls)
        self.assertNotIn("install -r", calls)

    def test_missing_installation_uses_saved_apk(self) -> None:
        result, calls = self.run_mock_start(installed=False)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("install -r", calls)
        self.assertIn("安装包/淇澳背单词-1.0.apk", calls)

    def test_wrong_emulator_is_not_modified(self) -> None:
        result, calls = self.run_mock_start(avd="Other_Device")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("端口 5554", result.stderr)
        self.assertNotIn("install -r", calls)
        self.assertNotIn("am start", calls)

    def test_scripts_have_valid_bash_syntax(self) -> None:
        for name in ["scripts/android-env.sh", "启动淇澳背单词.command"]:
            with self.subTest(name=name):
                subprocess.run(["bash", "-n", str(ROOT / name)], check=True)

    def test_environment_resolves_from_outside_project(self) -> None:
        result = subprocess.run(
            ["bash", "-c", 'source "$1"; printf "%s\\n" "$JAVA_HOME" "$ANDROID_HOME" "$ANDROID_SDK_ROOT"',
             "environment-test", str(ROOT / "scripts/android-env.sh")],
            cwd="/", check=True, capture_output=True, text=True,
        )
        java, sdk, sdk_root = result.stdout.splitlines()
        self.assertEqual(str(ROOT / ".local-runtime/jdk/jdk-17.0.20.1+1/Contents/Home"), java)
        self.assertEqual(str(ROOT / ".local-runtime/sdk"), sdk)
        self.assertEqual(sdk, sdk_root)
        self.assertNotIn("/private/tmp", result.stdout)

    def test_restart_keeps_data_and_uses_archived_apk(self) -> None:
        script = (ROOT / "启动淇澳背单词.command").read_text()
        self.assertIn("安装包/淇澳背单词-1.0.apk", script)
        self.assertIn("shell pm path com.morningwords", script)
        self.assertIn('install -r "$APK"', script)
        self.assertNotIn("-wipe-data", script)
        self.assertNotIn("shell pm clear", script)
        self.assertNotIn('"$SERIAL" uninstall', script)
        self.assertIn('actual_avd', script)

    def test_local_backups_are_not_committed(self) -> None:
        ignored = (ROOT / ".gitignore").read_text().splitlines()
        for directory in [".local-runtime/", "backups/", "安装包/"]:
            self.assertIn(directory, ignored)

    def test_recovery_guide_protects_learning_data_from_device_tests(self) -> None:
        guide = (ROOT / "重启与恢复说明.md").read_text()
        self.assertIn("必须使用专用测试模拟器", guide)
        self.assertIn("```bash\nbash\n", guide)
