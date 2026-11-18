#!/usr/bin/env python3
import pathlib
import subprocess
import tempfile

script = pathlib.Path(__file__).resolve().parents[2] / 'thor/configure-codex.py'
with tempfile.TemporaryDirectory() as temporary:
    path = pathlib.Path(temporary) / 'config.toml'
    path.write_text('# personal settings\nmodel = "model-with-[bracket"\nsandbox_mode = "read-only"\n\n[mcp_servers.other]\ncommand = "unchanged"\n')
    subprocess.run(['python3', str(script), str(path)], check=True)
    first = path.read_text()
    assert 'sandbox_mode = "danger-full-access"' in first
    assert 'approval_policy = "on-request"' in first
    assert 'model = "model-with-[bracket"' in first
    assert '[mcp_servers.other]\ncommand = "unchanged"\n' in first
    subprocess.run(['python3', str(script), str(path)], check=True)
    assert path.read_text() == first
    assert path.stat().st_mode & 0o777 == 0o600
    path.write_text('[broken\n')
    failed = subprocess.run(['python3', str(script), str(path)], capture_output=True)
    assert failed.returncode != 0
    assert path.read_text() == '[broken\n'
print('Codex config preservation/idempotence: OK')
