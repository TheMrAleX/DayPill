"""H5 checks on a disposable emulator. No test receiver, fixture or permission is shipped.

The first delivery uses the real AlarmManager after killing the background process.
Later retries advance the emulator clock and explicitly dispatch the private receiver
as root, avoiding wall-clock waits / idle quotas. Actions use the actual system UI.
"""
from pathlib import Path
from datetime import datetime, timezone
import re
import sqlite3
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

OUT = Path('ui-verification')
OUT.mkdir(exist_ok=True)
PACKAGE = 'app.pastillero'


def adb(*args):
    return subprocess.check_output(['adb', *map(str, args)], timeout=45)


def hierarchy():
    adb('shell', 'uiautomator', 'dump', '/sdcard/window.xml')
    data = adb('exec-out', 'cat', '/sdcard/window.xml')
    return data, ET.fromstring(data)


def values(node):
    return {v for n in node.iter('node') for v in [n.get('text'), n.get('content-desc')] if v}


def wait_label(label):
    for _ in range(10):
        data, root = hierarchy()
        if label.casefold() in {v.casefold() for v in values(root)}:
            return root
        time.sleep(.5)
    (OUT / 'failure.xml').write_bytes(data)
    raise AssertionError(f'Missing {label}: {values(root)}')


def bounds(node):
    return list(map(int, re.findall(r'\d+', node.get('bounds', ''))))


def tap_node(node):
    x1, y1, x2, y2 = bounds(node)
    adb('shell', 'input', 'tap', (x1+x2)//2, (y1+y2)//2)
    time.sleep(.7)


def tap(label):
    root = wait_label(label)
    candidates = [n for n in root.iter('node') if label.casefold() in {n.get('text', '').casefold(), n.get('content-desc', '').casefold()}]
    tap_node(max(candidates, key=lambda n: bounds(n)[1]))


def capture(name):
    data, _ = hierarchy()
    (OUT / f'{name}.xml').write_bytes(data)
    (OUT / f'{name}.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    print(f'PASS capture {name}', flush=True)


def database():
    # Snapshot the debug app's real SQLite/WAL files after each action has settled.
    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory) / 'pastillero.db'
        path.write_bytes(adb('exec-out', 'run-as', PACKAGE, 'cat', 'databases/pastillero.db'))
        exists = adb('shell', 'run-as', PACKAGE, 'ls', 'databases').decode()
        if 'pastillero.db-wal' in exists:
            Path(str(path)+'-wal').write_bytes(adb('exec-out', 'run-as', PACKAGE, 'cat', 'databases/pastillero.db-wal'))
        connection = sqlite3.connect(path)
        connection.row_factory = sqlite3.Row
        rows = [dict(r) for r in connection.execute('SELECT * FROM dose_logs ORDER BY localDate, minuteOfDay, id')]
        connection.close()
        return rows


def row(name):
    return next(r for r in database() if r['nameSnapshot'] == name and r['localDate'] == '2026-10-16')


def await_condition(description, predicate, seconds=15):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if predicate():
            print('PASS '+description, flush=True)
            return
        time.sleep(.5)
    raise AssertionError(description)


def alarm_dump(name):
    data = adb('shell', 'dumpsys', 'alarm').decode()
    (OUT / f'{name}-alarms.txt').write_text(data)
    # Pending alarms have a separate type/whenElapsed line, unlike historical stats.
    return [l for l in data.splitlines() if re.search(r'RTC_WAKEUP #\d+:.*app\.pastillero', l)]


def notification_dump(name):
    data = adb('shell', 'dumpsys', 'notification', '--noredact').decode()
    (OUT / f'{name}-notifications.txt').write_text(data)
    return data


def notification_action(title, action):
    adb('shell', 'cmd', 'statusbar', 'expand-notifications')
    root = wait_label(title)
    # Expand the target notification's smallest container with an expand control.
    candidates = [n for n in root.iter('node') if title in values(n) and any(x.get('resource-id', '').endswith('/expand_button') for x in n.iter('node'))]
    if candidates:
        card = min(candidates, key=lambda n: (bounds(n)[2]-bounds(n)[0])*(bounds(n)[3]-bounds(n)[1]))
        controls = [n for n in card.iter('node') if n.get('resource-id', '').endswith('/expand_button')]
        # Tapping a collapsed header expands it; if already expanded, the action is present.
        if action.casefold() not in {v.casefold() for v in values(card)}:
            tap_node(controls[0])
    root = wait_label(action)
    candidates = [n for n in root.iter('node') if title in values(n) and action.casefold() in {v.casefold() for v in values(n)}]
    card = min(candidates, key=lambda n: (bounds(n)[2]-bounds(n)[0])*(bounds(n)[3]-bounds(n)[1]))
    buttons = [n for n in card.iter('node') if n.get('text', '').casefold() == action.casefold()]
    assert buttons, values(card)
    tap_node(buttons[0])
    adb('shell', 'cmd', 'statusbar', 'collapse')


def create(name):
    tap('Medications')
    tap('Add medication')
    root = wait_label('Name')
    fields = [n for n in root.iter('node') if n.get('class') == 'android.widget.EditText']
    tap_node(fields[0])
    adb('shell', 'input', 'text', name)
    # Hide IME only when it is actually shown.
    ime = adb('shell', 'dumpsys', 'input_method').decode()
    if 'mInputShown=true' in ime or 'mIsInputViewShown=true' in ime:
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    tap('Save')
    wait_label(name)


def set_time(timestamp):
    adb('shell', 'cmd', 'alarm', 'set-time', int(timestamp))
    time.sleep(1)


def dispatch_alarm():
    adb('shell', 'am', 'broadcast', '-n', PACKAGE+'/.alarm.DoseAlarmReceiver', '-a', 'app.pastillero.DOSE_ALARM')


def wait_boot():
    adb('wait-for-device')
    deadline = time.monotonic()+180
    while time.monotonic() < deadline:
        if adb('shell', 'getprop', 'sys.boot_completed').strip() == b'1':
            time.sleep(3)
            return
        time.sleep(2)
    raise AssertionError('Emulator boot did not finish')


try:
    adb('root')
    adb('wait-for-device')
    adb('logcat', '-c')
    adb('shell', 'settings', 'put', 'global', 'auto_time', '0')
    adb('shell', 'settings', 'put', 'global', 'auto_time_zone', '0')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    adb('shell', 'settings', 'put', 'system', 'time_12_24', '24')
    adb('shell', 'cmd', 'uimode', 'night', 'no')
    adb('shell', 'wm', 'size', '1440x3120')
    adb('shell', 'wm', 'density', '560')
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'wm', 'dismiss-keyguard')
    adb('shell', 'cmd', 'alarm', 'set-timezone', 'UTC')
    adb('shell', 'pm', 'clear', PACKAGE)
    adb('shell', 'pm', 'grant', PACKAGE, 'android.permission.POST_NOTIFICATIONS')
    adb('shell', 'cmd', 'locale', 'set-app-locales', PACKAGE, '--locales', 'en-US')
    initial = int(datetime(2026,10,16,7,58,tzinfo=timezone.utc).timestamp()*1000)
    set_time(initial)
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE+'/.MainActivity')
    create('ReminderA')
    create('ReminderB')
    capture('scheduled-medications')
    await_condition('one exact RTC_WAKEUP alarm for simultaneous doses', lambda: len(alarm_dump('scheduled')) == 1)
    adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    adb('shell', 'am', 'kill', PACKAGE) # not force-stop: preserve AlarmManager state
    # pidof returns nonzero when absent, so use a shell conditional for this assertion.
    assert adb('shell', 'pidof app.pastillero || true').strip() == b''
    await_condition('real AlarmManager delivery restarts background process and posts both doses',
                    lambda: row('ReminderA')['repeatCount'] == 1 and row('ReminderB')['repeatCount'] == 1, seconds=150)
    dump = notification_dump('initial')
    assert 'ReminderA' in dump and 'ReminderB' in dump and 'Snooze' in dump and 'Taken' in dump
    assert 'Time for your medication' in dump
    adb('shell', 'cmd', 'statusbar', 'expand-notifications')
    capture('real-background-notifications')
    before = row('ReminderA')
    notification_action('ReminderA', 'Snooze')
    await_condition('Snooze preserves PENDING and repeat count', lambda: row('ReminderA')['status'] == 'PENDING' and row('ReminderA')['repeatCount'] == 1 and row('ReminderA')['notifyAt'] != before['notifyAt'])
    notification_action('ReminderB', 'Taken')
    await_condition('Taken persists timestamp and silences reminder', lambda: row('ReminderB')['status'] == 'TAKEN' and row('ReminderB')['takenAt'] is not None and row('ReminderB')['notifyAt'] is None)
    taken = row('ReminderB')
    for expected in [2, 3, 3]:
        previous = row('ReminderA')
        set_time(previous['notifyAt']+1000)
        dispatch_alarm()
        await_condition(f'retry delivery count {expected}', lambda: row('ReminderA')['repeatCount'] == expected and row('ReminderA')['notifyAt'] != previous['notifyAt'])
    assert row('ReminderA')['notifyAt'] is None
    assert row('ReminderB') == taken
    notification_dump('final-repeat')
    adb('shell', 'cmd', 'statusbar', 'expand-notifications')
    capture('last-repeat-notification')
    adb('shell', 'cmd', 'statusbar', 'collapse')
    # Opening an already running task from a notification must select Today.
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE+'/.MainActivity')
    tap('Medications')
    adb('shell', 'cmd', 'statusbar', 'expand-notifications')
    tap('ReminderA')
    wait_label('Settings')
    capture('notification-body-opens-today')
    before_future = next(r for r in database() if r['nameSnapshot'] == 'ReminderA' and r['localDate'] == '2026-10-17')
    adb('shell', 'cmd', 'alarm', 'set-timezone', 'Europe/Madrid')
    await_condition('TIMEZONE_CHANGED rebases future scheduled time', lambda: next(r for r in database() if r['id'] == before_future['id'])['scheduledAt'] == before_future['scheduledAt']-7200000)
    assert row('ReminderB') == taken
    await_condition('time change leaves exactly one pending alarm', lambda: len(alarm_dump('timezone')) == 1)
    # A real OS restart exercises BOOT_COMPLETED rather than an exported test hook.
    adb('reboot')
    wait_boot()
    await_condition('BOOT_COMPLETED restores a single alarm without opening the app', lambda: len(alarm_dump('boot')) == 1)
    assert row('ReminderB') == taken
    adb('install', '-r', 'app/build/outputs/apk/debug/app-debug.apk')
    await_condition('MY_PACKAGE_REPLACED restores a single alarm', lambda: len(alarm_dump('replaced')) == 1)
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'wm', 'dismiss-keyguard')
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE+'/.MainActivity')
    tap('Medications')
    for name in ['ReminderA', 'ReminderB']:
        tap(name)
        tap('Delete')
        tap('Delete')
        wait_label('Medications')
    await_condition('archiving last medication cancels the pending alarm', lambda: len(alarm_dump('archived')) == 0)
    tap('History')
    capture('history-after-notification-actions-and-archive')
    log = adb('logcat', '-d', '-v', 'brief')
    (OUT / 'h5-logcat.txt').write_bytes(log)
    assert b'Reminder broadcast could not complete' not in log
    assert not (b'FATAL EXCEPTION' in log and b'Process: app.pastillero' in log)
    print('PASS H5: real background alarm, separate notifications, system actions, repeat limit, Today navigation, time/zone, reboot, replacement and cancellation', flush=True)
finally:
    (OUT / 'h5-final-logcat.txt').write_bytes(adb('logcat', '-d', '-v', 'brief'))
    (OUT / 'h5-final.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    try:
        data, _ = hierarchy()
        (OUT / 'h5-final.xml').write_bytes(data)
    except Exception:
        pass
