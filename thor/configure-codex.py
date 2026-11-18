#!/usr/bin/env python3
"""Set the PRoot-required top-level options, preserving other CLI configuration."""
import os
from pathlib import Path
import re
import sys
import tempfile
import tomllib

path = Path(sys.argv[1])
source = path.read_text() if path.exists() else ''
try:
    tomllib.loads(source)
except tomllib.TOMLDecodeError:
    sys.exit('Configuration Codex invalide : fichier laissé intact.')
lines = source.splitlines(keepends=True)
section = next((i for i, line in enumerate(lines) if re.match(r'^[ \t]*\[', line)), len(lines))
head = ''.join(lines[:section])
tail = ''.join(lines[section:])
for key, value in [('sandbox_mode', 'danger-full-access'), ('approval_policy', 'on-request')]:
    line = f'{key} = "{value}"'
    pattern = rf'(?m)^[ \t]*{key}[ \t]*=.*$'
    if re.search(pattern, head):
        head = re.sub(pattern, line, head)
    else:
        head = head.rstrip('\n') + '\n' + line + '\n'
result = head + tail
if result != source or not path.exists():
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as output:
            output.write(result)
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)
path.chmod(0o600)
