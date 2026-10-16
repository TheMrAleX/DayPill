"""Exercise H4 through the real Android UI; fixtures exist only in the emulator."""
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

OUTPUT = Path('ui-verification')
OUTPUT.mkdir(exist_ok=True)
CURRENT_LANGUAGE = 'en'
SPANISH = {'Today': 'Hoy', 'Medications': 'Medicamentos', 'History': 'Historial',
           'Settings': 'Ajustes', 'Back': 'Atrás', 'New': 'Nuevo', 'Edit': 'Editar',
           'Add medication': 'Añadir medicamento', 'Save': 'Guardar', 'Cancel': 'Cancelar',
           'Delete': 'Eliminar', 'Delete medication?': '¿Eliminar medicamento?',
           'Add time': 'Añadir hora', 'Color': 'Color', 'Days': 'Días',
           'Mark as taken': 'Marcar como tomada', 'Mark as pending': 'Marcar como pendiente',
           'All done': 'Todo listo', 'No medications': 'Sin medicamentos',
           'Nothing for today': 'Nada para hoy', 'No history': 'Sin historial',
           'Enter a name': 'Escribe un nombre', 'Notifications disabled': 'Notificaciones desactivadas'}


def text(label):
    return SPANISH.get(label, label) if CURRENT_LANGUAGE == 'es' else label


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
    label = text(label)
    for _ in range(10):
        data, root = hierarchy()
        if label in labels(root):
            return data, root
        time.sleep(1)
    (OUTPUT / 'failure.xml').write_bytes(data)
    (OUTPUT / 'failure.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    raise AssertionError(f'Expected: {label}; actual: {labels(root)}')


def tap_node(node):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds', '')))
    assert x2 > x1 and y2 > y1, ET.tostring(node)
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(.6)


def tap(label):
    _, root = wait_for(label)
    matches = [node for node in root.iter('node') if text(label) in (node.get('text'), node.get('content-desc'))]
    tap_node(max(matches, key=lambda n: int(re.findall(r'\d+', n.get('bounds'))[1])))


def enter_field(index, value):
    _, root = hierarchy()
    fields = [node for node in root.iter('node') if node.get('class') == 'android.widget.EditText']
    assert len(fields) > index, labels(root)
    tap_node(fields[index])
    adb('shell', 'input', 'keyevent', '123', *(['67'] * 42))
    adb('shell', 'input', 'text', value.replace(' ', '%s'))
    time.sleep(.5)


def hide_keyboard():
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(.7)


def capture(name, expected, main=True):
    data, root = wait_for(expected)
    names = labels(root)
    for tab in ['Today', 'Medications', 'History']:
        assert (text(tab) in names) == main, (name, tab, names)
    (OUTPUT / f'{name}.xml').write_bytes(data)
    (OUTPUT / f'{name}.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    print(f'PASS {name}', flush=True)


def launch():
    adb('shell', 'am', 'force-stop', 'app.pastillero')
    adb('shell', 'am', 'start', '-W', '-f', '0x10008000', '-n', 'app.pastillero/.MainActivity')
    wait_for('Today')


def scroll_down():
    adb('shell', 'input', 'swipe', '720', '2200', '720', '800', '500')
    time.sleep(.6)


try:
    adb('logcat', '-c')
    adb('shell', 'wm', 'size', '1440x3120')
    adb('shell', 'wm', 'density', '560')
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'wm', 'dismiss-keyguard')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    adb('shell', 'settings', 'put', 'system', 'time_12_24', '24')
    adb('shell', 'cmd', 'uimode', 'night', 'no')
    adb('shell', 'cmd', 'locale', 'set-app-locales', 'app.pastillero', '--locales', 'en-US')
    launch()
    capture('empty-today', 'Nothing for today')
    tap('History')
    capture('empty-history', 'No history')
    tap('Medications')
    capture('empty-medications', 'No medications')
    tap('Add medication')
    tap('Save')
    capture('name-validation', 'Enter a name', False)
    enter_field(0, 'Aspirin')
    enter_field(1, '1 tablet')
    hide_keyboard()
    # Real configuration change: the draft must survive without being saved.
    adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
    adb('shell', 'settings', 'put', 'system', 'user_rotation', '1')
    time.sleep(2)
    wait_for('Aspirin')
    adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
    time.sleep(2)
    capture('new-draft-after-rotation', 'Aspirin', False)
    tap('Add time')
    capture('time-picker', 'Cancel', False)
    tap('Save')  # duplicate default 08:00 must be ignored
    _, root = hierarchy()
    assert len([n for n in root.iter('node') if n.get('text') == '08:00']) == 1
    tap('Save')
    # First save asks only after persistence. Deny once, then verify the in-app banner.
    for _ in range(10):
        data, root = hierarchy()
        denial = [n for n in root.iter('node') if n.get('resource-id', '').endswith('permission_deny_button')]
        if denial:
            (OUTPUT / 'notification-permission.xml').write_bytes(data)
            (OUTPUT / 'notification-permission.png').write_bytes(adb('exec-out', 'screencap', '-p'))
            tap_node(denial[0])
            break
        time.sleep(1)
    else:
        raise AssertionError(f'First save did not request notification permission: {labels(root)}')
    wait_for('Aspirin')
    launch()
    capture('notification-denied-banner', 'Notifications disabled')
    adb('shell', 'pm', 'grant', 'app.pastillero', 'android.permission.POST_NOTIFICATIONS')
    launch()
    capture('today-pending', 'Mark as taken')
    tap('Mark as taken')
    capture('today-taken', 'All done')
    tap('Mark as pending')
    wait_for('Mark as taken')
    tap('Mark as taken')
    launch()
    capture('taken-survives-restart', 'All done')
    tap('History')
    capture('history-taken', '100 %')
    tap('Medications')
    tap('Aspirin')
    enter_field(1, '2 tablets')
    hide_keyboard()
    tap('Save')
    wait_for('Aspirin')
    tap('Aspirin')
    capture('edited-dose-persisted', '2 tablets', False)
    tap('Back')
    for language in ['en', 'es']:
        CURRENT_LANGUAGE = language
        adb('shell', 'cmd', 'locale', 'set-app-locales', 'app.pastillero', '--locales', 'en-US' if language == 'en' else 'es-ES')
        for mode in ['light', 'dark']:
            adb('shell', 'cmd', 'uimode', 'night', 'yes' if mode == 'dark' else 'no')
            for scale in ['1.0', '2.0']:
                adb('shell', 'settings', 'put', 'system', 'font_scale', scale)
                prefix = f'{language}-{mode}-font{scale}'
                launch()
                capture(prefix + '-today', 'All done')
                tap('Medications')
                capture(prefix + '-medications', 'Aspirin')
                tap('Aspirin')
                capture(prefix + '-edit', 'Edit', False)
                scroll_down()
                capture(prefix + '-selectors', 'Color', False)
                tap('Back')
                tap('History')
                capture(prefix + '-history', '100 %')
    # Archive only after capturing the populated states; preserve the completed snapshot.
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    launch()
    tap('Medications')
    tap('Aspirin')
    tap('Delete')
    capture('delete-confirmation', 'Delete medication?', False)
    tap('Cancel')
    wait_for('Edit')
    tap('Delete')
    tap('Delete')
    capture('archived-medication', 'No medications')
    tap('History')
    capture('archived-history-preserved', '100 %')
    tap('Today')
    capture('archived-taken-preserved', 'All done')
    log = adb('logcat', '-d', '-v', 'brief', 'AndroidRuntime:E', '*:S')
    (OUTPUT / 'runtime.log').write_bytes(log)
    assert not (b'FATAL EXCEPTION' in log and b'Process: app.pastillero' in log), log.decode(errors='replace')
    print('PASS H4: validation, rotation, duplicate time, first permission, toggle, restart, edit, archive/history, both themes/languages/font scales', flush=True)
finally:
    (OUTPUT / 'logcat.txt').write_bytes(adb('logcat', '-d', '-v', 'brief'))
