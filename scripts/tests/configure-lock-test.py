"""Configuration can be repeated while detached bridge processes remain alive."""
import fcntl
import os
from pathlib import Path
import shutil
import signal
import subprocess
import tempfile
import time
import unittest


ROOT = Path(__file__).resolve().parents[2]


class ConfigureLockTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.base = Path(self.directory.name)
        self.home = self.base / "home"
        self.bin = self.home / "bin"
        self.bin.mkdir(parents=True)
        self.state = self.home / ".local/state/thor"
        self.state.mkdir(parents=True)
        self.token_file = self.base / "prefix/var/lib/proot-distro/containers/debian/rootfs/home/dev/.config/regie/bridge-token"
        self.token_file.parent.mkdir(parents=True)
        self.env = dict(os.environ, HOME=str(self.home), PREFIX=str(self.base / "prefix"),
                        PATH=f"{self.bin}:{os.environ['PATH']}")
        self.run_number = 0
        self.addCleanup(self.stop_children)
        # Real kernel locks, including on macOS where util-linux's CLI is absent.
        if not shutil.which("flock"):
            self.script("flock", """
import fcntl, sys
fcntl.flock(int(sys.argv[-1]), fcntl.LOCK_EX)
""")
        if not shutil.which("setsid"):
            self.script("setsid", """
import os, sys
os.setsid()
os.execvp(sys.argv[1], sys.argv[1:])
""")
        # Only external boundaries are fixtures: no Debian, network or agents.
        self.script("proot-distro", """
import os, pathlib
print((pathlib.Path(os.environ['HOME']) / 'token-source').read_text(), end='')
""")
        self.script("curl", """
import os, pathlib, time
home = pathlib.Path(os.environ['HOME'])
ready = home / f"child-{os.environ['FIXTURE_RUN']}-regie-bridge-supervise.pid"
deadline = time.monotonic() + 2
while not ready.exists():
    assert time.monotonic() < deadline, 'Supervisor fixture did not start'
    time.sleep(0.01)
""")
        for name in ("regie-bridge-supervise", "regie-android-access"):
            self.script(name, """
import os, pathlib, signal
home = pathlib.Path(os.environ['HOME'])
if pathlib.Path(__file__).name == 'regie-bridge-supervise':
    (home / '.local/state/thor/regie-bridge-supervisor.pid').write_text(str(os.getpid()))
(home / f"child-{os.environ['FIXTURE_RUN']}-{pathlib.Path(__file__).name}.pid").write_text(str(os.getpid()))
signal.pause()
""")
        self.script("regie-bridge-stop", """
import os, pathlib, signal
home = pathlib.Path(os.environ['HOME'])
with (home / 'stops').open('a') as calls:
    calls.write('stop\\n')
pid = home / '.local/state/thor/regie-bridge-supervisor.pid'
if pid.exists():
    try:
        os.kill(int(pid.read_text()), signal.SIGTERM)
    except ProcessLookupError:
        pass
""")
        # Preserve the real start script; only its platform shebang changes.
        start = (ROOT / "thor/bridge/start.sh").read_text().split("\n", 1)[1]
        self.executable("regie-bridge-start", "#!/usr/bin/env bash\n" + start)

    def executable(self, name, source):
        path = self.bin / name
        path.write_text(source)
        path.chmod(0o700)

    def script(self, name, source):
        self.executable(name, "#!/usr/bin/env python3\n" + source.lstrip())

    def stop_children(self):
        for path in self.home.glob("child-*.pid"):
            try:
                os.kill(int(path.read_text()), signal.SIGTERM)
            except ProcessLookupError:
                pass

    def configure(self, token, client=None):
        self.run_number += 1
        self.env['FIXTURE_RUN'] = str(self.run_number)
        (self.home / "token-source").write_text(token)
        target = self.token_file if client is None else self.token_file.parent / "bridge-tokens" / client
        with subprocess.Popen(
            ["bash", str(ROOT / "thor/bridge/configure.sh"), "regie-pair-fixture",
             *([] if client is None else [client])],
            env=self.env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, start_new_session=True,
        ) as process:
            try:
                stdout, stderr = process.communicate(timeout=3)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.communicate()
                self.fail("A successive configuration is blocked by the detached bridge's lock")
            self.assertEqual(process.returncode, 0, "Configuration failed")
        self.assertNotIn(token.encode(), stdout + stderr)
        self.assertEqual(target.read_text(), token)
        self.assertEqual(target.stat().st_mode & 0o777, 0o600)
        # start also detaches Android access when a supervisor already runs.
        # Await its handshake so cleanup cannot race a late fixture startup.
        deadline = time.monotonic() + 2
        while not (self.home / f"child-{self.run_number}-regie-android-access.pid").exists():
            self.assertLess(time.monotonic(), deadline, "Android access fixture did not start")
            time.sleep(0.01)

    def test_successive_configurations_release_the_lock_with_detached_processes_alive(self):
        self.configure("ab" * 32)
        deadline = time.monotonic() + 2
        while len(list(self.home.glob("child-*.pid"))) < 2:
            self.assertLess(time.monotonic(), deadline, "Detached fixtures did not start")
            time.sleep(0.01)
        for path in self.home.glob("child-*.pid"):
            os.kill(int(path.read_text()), 0)
        # The new app can replace its predecessor's token while the bridge runs.
        self.configure("cd" * 32)
        # Same-token exec path must not leak the lock either.
        self.configure("cd" * 32)
        self.configure("ef" * 32)
        self.assertEqual((self.home / "stops").read_text(), "stop\nstop\nstop\n")
        with (self.state / "regie-bridge-configure.lock").open("w") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)

    def test_a_second_app_joins_and_renews_its_token_without_stopping_the_bridge(self):
        self.configure("ab" * 32)
        self.configure("cd" * 32, client="app.regie.jeu")
        self.configure("cd" * 32, client="app.regie.jeu")
        self.configure("ef" * 32, client="app.regie.jeu")
        self.configure("ab" * 32)
        self.assertEqual(self.token_file.read_text(), "ab" * 32)
        # Only the change of the historical token stops the bridge; the app tokens never do.
        self.assertEqual((self.home / "stops").read_text(), "stop\n")
        with (self.state / "regie-bridge-configure.lock").open("w") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)


if __name__ == "__main__":
    unittest.main()
