"""Verify the downloaded Stitch snapshot without requiring network or API keys."""
import hashlib
from html.parser import HTMLParser
import json
from pathlib import Path
import struct
import unittest
import zlib

ROOT = Path(__file__).resolve().parents[1] / 'design/stitch/2944537851381759161'
EXPECTED_IDS = {
    'fb6642b640994e23af47047fc7522fd2',
    '121f733cc8b44349bdf72d5719640390',
    '385eac36e49f411283c4b9b46d2fa6d4',
    '9be5ab715ce14feb8f78203bd10afb91',
    'd31a68c3b7c44da2b1bd85bad09833c0',
}


class Images(HTMLParser):
    def __init__(self):
        super().__init__()
        self.sources = []
        self.tags = set()

    def handle_starttag(self, tag, attrs):
        self.tags.add(tag)
        if tag == 'img':
            self.sources.append(dict(attrs)['src'])


class StitchExportTest(unittest.TestCase):
    def test_manifest_has_exact_files_and_valid_hashes(self):
        manifest = json.loads((ROOT / 'manifest.json').read_text())
        documented = {entry['path'] for entry in manifest['files']}
        actual = {p.relative_to(ROOT).as_posix() for p in ROOT.rglob('*')
                  if p.is_file() and p.name != 'manifest.json'}
        self.assertEqual(actual, documented)
        for entry in manifest['files']:
            with self.subTest(path=entry['path']):
                data = (ROOT / entry['path']).read_bytes()
                self.assertEqual(len(data), entry['bytes'])
                self.assertEqual(hashlib.sha256(data).hexdigest(), entry['sha256'])

    def test_requested_screen_ids_and_local_preview_images(self):
        screens = json.loads((ROOT / 'screens.json').read_text())
        self.assertEqual({s['id'] for s in screens}, EXPECTED_IDS)
        for screen in screens:
            folder = ROOT / screen['folder']
            for name in ('code.html', 'preview.html'):
                parser = Images()
                parser.feed((folder / name).read_text())
                self.assertTrue({'html', 'head', 'body'}.issubset(parser.tags))
                if name == 'preview.html':
                    for src in parser.sources:
                        self.assertFalse(src.startswith(('http:', 'https:')))
                        self.assertTrue((folder / src).is_file())

    def test_png_checksums_dimensions_and_complete_stream(self):
        screens = json.loads((ROOT / 'screens.json').read_text())
        expected = {s['folder'] + '/screen.png': (int(s['width']), int(s['height'])) for s in screens}
        for path in ROOT.rglob('*.png'):
            with self.subTest(path=path.name):
                data = path.read_bytes()
                self.assertEqual(data[:8], b'\x89PNG\r\n\x1a\n')
                offset, kinds, compressed = 8, [], bytearray()
                while offset < len(data):
                    size = struct.unpack('>I', data[offset:offset + 4])[0]
                    kind = data[offset + 4:offset + 8]
                    payload = data[offset + 8:offset + 8 + size]
                    crc = struct.unpack('>I', data[offset + 8 + size:offset + 12 + size])[0]
                    self.assertEqual(zlib.crc32(kind + payload) & 0xffffffff, crc)
                    kinds.append(kind)
                    if kind == b'IDAT':
                        compressed.extend(payload)
                    offset += 12 + size
                self.assertEqual(kinds[0], b'IHDR')
                self.assertEqual(kinds[-1], b'IEND')
                self.assertEqual(offset, len(data))
                self.assertTrue(zlib.decompress(compressed))
                relative = path.relative_to(ROOT).as_posix()
                if relative in expected:
                    self.assertEqual(struct.unpack('>II', data[16:24]), expected[relative])

    def test_design_system_asset_is_not_misrepresented_as_screen(self):
        asset = json.loads((ROOT / 'design-system/asset.json').read_text())
        manifest = json.loads((ROOT / 'manifest.json').read_text())
        self.assertEqual(asset['name'], manifest['designSystem']['assetName'])
        self.assertEqual(manifest['designSystem']['requestedId'], 'asset-stub-' + asset['name'].replace('/', '_'))
        self.assertEqual((ROOT / 'design-system/DESIGN.md').read_text().rstrip(),
                         asset['designSystem']['theme']['designMd'].rstrip())
        self.assertEqual(manifest['designSystem']['imageStatus'], 'not_available_from_mcp')
