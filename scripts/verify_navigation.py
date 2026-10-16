"""Smoke-check the H3 navigation and capture real Android emulator screenshots."""
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

OUTPUT = Path('ui-verification')
OUTPUT.mkdir(exist_ok=True)


def adb(*args):
    return subprocess.check_output(['adb', *args], timeout=45)


def hierarchy():
    adb('shell', 'uiautomator', 'dump', '/sdcard/window.xml')
    data = adb('exec-out', 'cat', '/sdcard/window.xml')
    return data, ET.fromstring(data)


def labels(root):
    return {value for node in root.iter('node')
            for value in [node.get('text'), node.get('content-desc')] if value}


def wait_for(label):
    for _ in range(10):
        data, root = hierarchy()
        if label in labels(root):
            return data, root
        time.sleep(1)
    raise AssertionError(f'Expected screen label: {label}; actual: {labels(root)}')


def tap(label):
    _, root = wait_for(label)
    matches = [node for node in root.iter('node')
               if label in (node.get('text'), node.get('content-desc'))]
    # Bottom navigation labels also occur in headers. Select the lower occurrence.
    bounds = [list(map(int, re.findall(r'\d+', n.get('bounds', '')))) for n in matches]
    bounds = [b for b in bounds if len(b) == 4 and b[2] > b[0] and b[3] > b[1]]
    assert bounds, f'No visible bounds for {label}'
    x1, y1, x2, y2 = max(bounds, key=lambda b: b[1])
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(1)


def capture(name, expected, main):
    data, root = wait_for(expected)
    names = labels(root)
    for tab in ['Today', 'Medications', 'History']:
        assert (tab in names) == main, (name, tab, names)
    (OUTPUT / f'{name}.xml').write_bytes(data)
    (OUTPUT / f'{name}.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    print(f'PASS {name}', flush=True)


def launch():
    adb('shell', 'am', 'force-stop', 'app.pastillero')
    adb('shell', 'am', 'start', '-W', '-f', '0x10008000', '-n', 'app.pastillero/.MainActivity')
    wait_for('Today')


try:
    adb('logcat', '-c')
    adb('shell', 'wm', 'size', '1440x3120')
    adb('shell', 'wm', 'density', '560')
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'wm', 'dismiss-keyguard')
    for mode in ['light', 'dark']:
        adb('shell', 'cmd', 'uimode', 'night', 'yes' if mode == 'dark' else 'no')
        adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
        launch()
        capture(f'{mode}-today', 'Settings', True)
        tap('Settings')
        capture(f'{mode}-settings', 'Back', False)
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        tap('Medications')
        capture(f'{mode}-medications', 'Add medication', True)
        tap('Add medication')
        capture(f'{mode}-new', 'New', False)
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        wait_for('Add medication')
        tap('History')
        capture(f'{mode}-history', 'History', True)
        adb('shell', 'settings', 'put', 'system', 'font_scale', '2.0')
        launch()
        capture(f'{mode}-today-font200', 'Settings', True)
        tap('Medications')
        capture(f'{mode}-medications-font200', 'Add medication', True)
    log = adb('logcat', '-d', '-v', 'brief', 'AndroidRuntime:E', '*:S')
    (OUTPUT / 'runtime.log').write_bytes(log)
    assert b'FATAL EXCEPTION' not in log, log.decode(errors='replace')
    print('PASS navigation, back, both system themes and 200% font captures', flush=True)
finally:
    (OUTPUT / 'logcat.txt').write_bytes(adb('logcat', '-d', '-v', 'brief'))
