#!/usr/bin/env python3
import os
from pathlib import Path
import subprocess
import tempfile

script = Path(__file__).resolve().parents[1] / 'release-key.py'
with tempfile.TemporaryDirectory() as directory:
    fixture = Path(directory)
    repo = fixture / 'repo'
    repo.mkdir()
    binary = fixture / 'bin'
    binary.mkdir()
    keytool = binary / 'keytool'
    keytool.write_text('''#!/usr/bin/env python3
import os, pathlib, sys
assert '-storepass:env' in sys.argv and '-keypass:env' in sys.argv
assert os.environ['REGIE_SIGN_PASSWORD'] not in ' '.join(sys.argv)
pathlib.Path(sys.argv[sys.argv.index('-keystore')+1]).write_text('fixture-keystore')
''')
    keytool.chmod(0o755)
    env = dict(os.environ, PATH=str(binary) + os.pathsep + os.environ['PATH'])
    config = fixture / 'external/release.properties'
    created = subprocess.run(['python3', str(script), str(config), str(repo)], env=env, capture_output=True, text=True, check=True)
    first = config.read_text()
    secret = next(line.split('=', 1)[1] for line in first.splitlines() if line.startswith('storePassword='))
    assert len(secret) == 64 and secret not in created.stdout + created.stderr
    assert config.stat().st_mode & 0o777 == 0o600
    subprocess.run(['python3', str(script), str(config), str(repo)], env=env, check=True)
    assert config.read_text() == first
    (config.parent / 'release.keystore').unlink()
    refused = subprocess.run(['python3', str(script), str(config), str(repo)], env=env, capture_output=True)
    assert refused.returncode != 0 and config.read_text() == first
    refused = subprocess.run(['python3', str(script), str(repo / 'release.properties'), str(repo)], env=env, capture_output=True)
    assert refused.returncode != 0 and not (repo / 'release.properties').exists()
print('external release key/no replacement/no secret output: OK')
