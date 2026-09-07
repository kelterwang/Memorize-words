from pathlib import Path
import importlib.util
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('stitch_setup', ROOT / 'scripts/configure_stitch_mcp.py')
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)


class StitchSetupTest(unittest.TestCase):
    def test_preserves_other_settings_and_enables_only_with_key(self):
        original = 'model = "existing"\n[mcp_servers.other]\nurl = "https://example.com"\n'
        prepared = setup.render_config(original)
        self.assertFalse(setup.tomllib.loads(prepared)['mcp_servers']['stitch']['enabled'])
        configured = setup.render_config(prepared, 'test-key')
        data = setup.tomllib.loads(configured)
        self.assertEqual(data['model'], 'existing')
        self.assertEqual(data['mcp_servers']['other']['url'], 'https://example.com')
        self.assertEqual(data['mcp_servers']['stitch']['http_headers']['X-Goog-Api-Key'], 'test-key')
        self.assertTrue(data['mcp_servers']['stitch']['enabled'])
        self.assertEqual(setup.render_config(configured, 'test-key'), configured)

    def test_does_not_overwrite_unmanaged_server(self):
        with self.assertRaises(ValueError):
            setup.render_config('[mcp_servers.stitch]\nurl="existing"\n')

    def test_rejects_invalid_key_before_writing(self):
        with self.assertRaises(ValueError):
            setup.render_config('', 'bad\nkey')

    def test_private_backup_and_config(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'config.toml'
            original = 'model="existing"\n'
            path.write_text(original)
            setup.write_config(path, 'test-key')
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            backups = list(path.parent.glob('config.before-stitch-*'))
            self.assertEqual(len(backups), 1)
            self.assertEqual(backups[0].read_text(), original)
            self.assertEqual(backups[0].stat().st_mode & 0o777, 0o600)

    def test_failed_authentication_does_not_write_config(self):
        with patch.object(setup.argparse.ArgumentParser, 'parse_args', return_value=type('Args', (), {'prepare': False})()), \
             patch.object(setup.getpass, 'getpass', return_value='test-key'), \
             patch.object(setup.Path, 'exists', return_value=False), \
             patch.object(setup, 'verify_key', side_effect=ValueError('rejected')), \
             patch.object(setup, 'write_config') as write:
            with self.assertRaises(ValueError):
                setup.main()
            write.assert_not_called()
