"""Offline CLI contract tests. adb is a temporary fake, never a device."""
import os
import struct
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent


class EmulatorCommands(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        adb = self.folder / 'adb'
        adb.write_text('''#!/bin/bash
printf '%s\\n' "$*" >> "$CALLS"
case "$*" in
  *"emu avd name") printf '%s\\nOK\\n' "${FAKE_AVD:-Regie_Thor}" ;;
  *"reverse tcp:47600 tcp:47600")
    if [[ "$(grep -c 'reverse tcp:47600 tcp:47600' "$CALLS")" == 1 ]]; then
      echo 'adb: error: cannot bind listener: Address already in use' >&2; exit 1
    fi
    echo 47600 ;;
  *"get-state") echo device ;;
  *"getprop sys.boot_completed") echo 1 ;;
  *"emu multidisplay add"*)
    if [[ "${FAKE_DISPLAY_DELAY:-}" == 1 ]] && [[ "$(grep -c 'emu multidisplay add' "$CALLS")" == 1 ]]; then
      echo 'KO: MultiDisplayPipe is not up yet'; exit 1
    fi
    echo OK ;;
  *"dumpsys SurfaceFlinger --display-id")
    echo 'Display 101 (HWC display 0): displayName="EMU_display_0"'
    [[ "${FAKE_ONE_SCREEN:-}" == 1 ]] || echo 'Display 202 (Virtual display): displayName="Emulator 2D Display"' ;;
  *"exec-out screencap -p -d 101") cat "$FAKE_TOP" ;;
  *"exec-out screencap -p -d 202") cat "$FAKE_BOTTOM" ;;
esac
''')
        adb.chmod(0o755)
        for name, dimensions in [('top', (1920, 1080)), ('bottom', (1240, 1080))]:
            (self.folder / name).write_bytes(b'\x89PNG\r\n\x1a\n' + b'\x00' * 8 + struct.pack('>II', *dimensions))
        self.calls = self.folder / 'calls'
        self.env = dict(os.environ, PATH=f'{self.folder}:{os.environ["PATH"]}',
                        CALLS=str(self.calls), EMU_SERIAL='emulator-5580',
                        FAKE_TOP=str(self.folder / 'top'), FAKE_BOTTOM=str(self.folder / 'bottom'))

    def run_script(self, name, *args):
        return subprocess.run(['bash', str(ROOT / name), *args], env=self.env,
                              capture_output=True, text=True)

    def test_bridge_refuses_to_reuse_a_bridge_from_another_worktree(self):
        bench = self.folder / 'bench'
        bench.mkdir()
        (bench / 'bridge-token').write_text('f' * 64)
        (bench / 'bridge-root').write_text('/another/worktree\n')
        self.env['REGIE_BENCH_DIR'] = str(bench)
        for name in ['curl', 'node', 'npm']:
            stub = self.folder / name
            stub.write_text('#!/bin/bash\nexit 0\n')
            stub.chmod(0o755)
        result = self.run_script('bridge-mac.sh')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('worktree', result.stderr)
        self.assertNotIn('reverse tcp:', self.calls.read_text())

    def test_reverse_retries_a_transient_busy_port_without_removing_other_listeners(self):
        result = subprocess.run(['bash', '-c', 'source "$1"; reverse_bridge', 'bench',
                                 str(ROOT / 'emu-common.sh')], env=self.env,
                                capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        calls = self.calls.read_text()
        self.assertEqual(2, calls.count('reverse tcp:47600 tcp:47600'))
        self.assertNotIn('--remove', calls)

    def test_start_waits_for_multidisplay_after_boot_and_reuses_only_its_avd(self):
        self.env['FAKE_DISPLAY_DELAY'] = '1'
        self.env['REGIE_BENCH_DIR'] = str(self.folder / 'bench')
        result = self.run_script('emu-start.sh')
        self.assertEqual(0, result.returncode, result.stderr)
        calls = self.calls.read_text()
        self.assertEqual(2, calls.count('emu multidisplay add 1 1240 1080 369 0'))
        self.assertIn('getprop sys.boot_completed', calls)

    def test_captures_use_surfaceflinger_ids_including_virtual_display(self):
        target = self.folder / 'captures'
        result = self.run_script('emu-shot.sh', str(target), 'demo-')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual((self.folder / 'top').read_bytes(), (target / 'demo-haut.png').read_bytes())
        self.assertEqual((self.folder / 'bottom').read_bytes(), (target / 'demo-bas.png').read_bytes())

    def test_missing_second_screen_fails_instead_of_claiming_a_dual_capture(self):
        self.env['FAKE_ONE_SCREEN'] = '1'
        result = self.run_script('emu-shot.sh', str(self.folder / 'captures'))
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn('exec-out screencap', self.calls.read_text())

    def test_invalid_capture_is_not_published(self):
        (self.folder / 'bottom').write_text('screencap failed')
        target = self.folder / 'captures'
        result = self.run_script('emu-shot.sh', str(target))
        self.assertNotEqual(0, result.returncode)
        self.assertFalse((target / 'haut.png').exists())
        self.assertFalse((target / 'bas.png').exists())

    def test_trigger_hold_uses_gamepad_source_and_requested_display(self):
        result = self.run_script('emu-key.sh', 'L2', '800', '1')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('-s emulator-5580 shell input gamepad -d 1 keyevent --duration 800 KEYCODE_BUTTON_L2', self.calls.read_text())

    def test_r2_and_a_are_real_android_button_codes(self):
        for key in ['R2', 'A']:
            result = self.run_script('emu-key.sh', key)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn(f'KEYCODE_BUTTON_{key}', self.calls.read_text())

    def test_another_avd_is_never_controlled(self):
        self.env['FAKE_AVD'] = 'Personal_Phone'
        result = self.run_script('emu-key.sh', 'A')
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn('shell input', self.calls.read_text())

    def test_a_physical_console_is_never_controlled(self):
        self.env['EMU_SERIAL'] = 'THOR0000000001'
        result = self.run_script('emu-key.sh', 'A')
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(self.calls.exists())

    def test_invalid_key_does_not_inject_an_event(self):
        result = self.run_script('emu-key.sh', 'UNKNOWN')
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn('shell input', self.calls.read_text() if self.calls.exists() else '')


if __name__ == '__main__':
    unittest.main()
