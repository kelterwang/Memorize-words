"""Configure the official Stitch MCP; keep credentials outside the repository."""
import argparse
import getpass
import json
import os
from pathlib import Path
import re
import tempfile
import urllib.request

try:
    import tomllib
except ImportError:
    from pip._vendor import tomli as tomllib

URL = 'https://stitch.googleapis.com/mcp'
BEGIN = '# BEGIN managed Stitch MCP\n'
END = '# END managed Stitch MCP\n'


def render_config(original, key=None):
    parsed = tomllib.loads(original)
    if BEGIN in original and END in original:
        start = original.index(BEGIN)
        stop = original.index(END, start) + len(END)
        remaining = original[:start] + original[stop:]
    else:
        if 'stitch' in parsed.get('mcp_servers', {}):
            raise ValueError('已有非本工具创建的 Stitch 配置，请先检查，避免覆盖。')
        remaining = original
    block = BEGIN + '[mcp_servers.stitch]\n'
    block += 'url = ' + json.dumps(URL) + '\n'
    block += 'enabled = ' + ('true' if key else 'false') + '\n'
    block += 'startup_timeout_sec = 30\ntool_timeout_sec = 300\n'
    if key:
        if not re.fullmatch(r'[A-Za-z0-9_.-]+', key):
            raise ValueError('密钥格式不正确，请只粘贴 API Key 本身。')
        block += 'http_headers = { "X-Goog-Api-Key" = ' + json.dumps(key) + ' }\n'
    block += END
    result = remaining.rstrip() + '\n\n' + block
    tomllib.loads(result)
    return result


def write_config(path, key=None):
    original = path.read_text() if path.exists() else ''
    updated = render_config(original, key)
    path.parent.mkdir(parents=True, exist_ok=True)
    if original:
        fd, backup = tempfile.mkstemp(prefix='config.before-stitch-', suffix='.toml', dir=path.parent)
        with os.fdopen(fd, 'w') as output:
            output.write(original)
    fd, temporary = tempfile.mkstemp(prefix='.stitch-config-', dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as output:
            output.write(updated)
        if path.exists() and path.read_text() != original:
            raise ValueError('配置被其他程序修改，请重试。')
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def verify_key(key):
    payload = {'jsonrpc': '2.0', 'id': 1, 'method': 'initialize', 'params': {
        'protocolVersion': '2024-11-05', 'capabilities': {},
        'clientInfo': {'name': 'local-stitch-setup', 'version': '1.0'}}}
    request = urllib.request.Request(URL, data=json.dumps(payload).encode(), headers={
        'Content-Type': 'application/json', 'Accept': 'application/json, text/event-stream',
        'X-Goog-Api-Key': key})
    with urllib.request.urlopen(request, timeout=30) as response:
        if 'text/event-stream' in response.headers.get('Content-Type', ''):
            for line in response:
                if line.startswith(b'data:'):
                    data = json.loads(line[5:])
                    if data.get('id') == 1:
                        break
            else:
                raise ValueError('服务没有返回初始化结果。')
        else:
            data = json.load(response)
    if 'result' not in data or 'serverInfo' not in data['result']:
        raise ValueError('Stitch 初始化未通过。')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prepare', action='store_true', help='仅创建未启用的配置，不录入密钥')
    args = parser.parse_args()
    path = Path(os.environ.get('CODEX_HOME', str(Path.home() / '.codex'))) / 'config.toml'
    if args.prepare:
        write_config(path)
        print('Stitch 配置已准备，等待 API Key，暂未启用。')
        return
    key = getpass.getpass('粘贴 Stitch API Key（不显示字符），然后按回车：').strip()
    if not key:
        raise ValueError('未输入密钥，配置未修改。')
    # Validate input before sending; never print the key or remote response body.
    render_config(path.read_text() if path.exists() else '', key)
    print('正在验证 Stitch 连接…')
    verify_key(key)
    write_config(path, key)
    print('Stitch 连接验证通过，已保存并启用。请在 MCP 设置中重启服务，或重启 Codex。')


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        print('\n已取消。')
        raise SystemExit(1)
    except Exception as error:
        # Exception text may contain request details; report only safe categories.
        print('配置未完成。请检查密钥、网络和本机配置。错误类型：' + type(error).__name__)
        raise SystemExit(1)
