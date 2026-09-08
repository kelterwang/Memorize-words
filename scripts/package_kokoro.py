"""Prepare the pinned Kokoro v1.0 English ZIP and the app's file integrity manifest.

Usage: python3 scripts/package_kokoro.py /path/to/kokoro-int8-multi-lang-v1_0
Download source: https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_0.tar.bz2
"""
import hashlib
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def package(source: Path, output: Path):
    required = ['model.int8.onnx', 'voices.bin', 'tokens.txt', 'lexicon-us-en.txt', 'lexicon-gb-en.txt', 'LICENSE']
    files = {name: source / name for name in required}
    files.update({p.relative_to(source).as_posix(): p for p in (source / 'espeak-ng-data').rglob('*') if p.is_file()})
    files['espeak-ng-COPYING'] = ROOT / 'third_party/espeak-ng-COPYING'
    hashes = {}
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED, compresslevel=5) as archive:
        for name, path in sorted(files.items()):
            data = path.read_bytes()
            hashes[name] = hashlib.sha256(data).hexdigest()
            info = zipfile.ZipInfo(name, date_time=(2026, 9, 8, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    (ROOT / 'app/src/main/assets/kokoro-files.json').write_text(json.dumps(hashes, indent=2) + '\n')
    bundled = ROOT / 'app/src/main/assets/kokoro-bundled'
    bundled.mkdir(parents=True, exist_ok=True)
    for old in bundled.glob('*.part'):
        old.unlink()
    with output.open('rb') as stream:
        index = 0
        while chunk := stream.read(48 * 1024 * 1024):
            (bundled / f'{index:03d}.part').write_bytes(chunk)
            index += 1
    print(output, output.stat().st_size, hashlib.sha256(output.read_bytes()).hexdigest())

if __name__ == '__main__':
    package(Path(sys.argv[1]), ROOT / '安装包/Kokoro-English-v1.0.zip')
