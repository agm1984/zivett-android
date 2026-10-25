#!/usr/bin/env python3
"""A screenshot tour of every role's screens, driven over adb — the
Android twin of the iOS app's ScreenTour UI test (2026-10-24).

It signs in as the seeded dev accounts against the local Sail server
(the emulator reaches it at 10.0.2.2:8090), opens every tab, every row of
the More / Account menus and the first job or invoice of each list, and
keeps a screenshot of each screen plus one scrolled view. It NEVER taps
anything that changes data — only tab labels, menu row titles, job/invoice
codes and Log in / Log out — so the dev database is left as it was.

    scripts/screen-tour.py <out-dir> [portrait|landscape]

Uses the first connected device (`adb devices`). Screens are named
`<orientation>-<role>-<screen>[-scrolled].png`.
"""

import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = os.path.expanduser('~/Library/Android/sdk/platform-tools/adb')
OUT = sys.argv[1] if len(sys.argv) > 1 else 'tour'
ORIENTATION = sys.argv[2] if len(sys.argv) > 2 else 'landscape'

ROLES = [
    ('customer', 'customer@ratedpro.ca', ['Home', 'Jobs', 'Book', 'Messages', 'Account'], 'Account',
     ['Profile', 'Notifications', 'Saved addresses', 'Invoices', 'Warranties', 'Refer a friend']),
    ('business', 'business@ratedpro.ca', ['Overview', 'Requests', 'Book', 'Properties', 'Account'], 'Account',
     ['My account', 'Business profile', 'Plan', 'Team', 'Notifications', 'Invoices', 'Warranties']),
    ('company', 'company@ratedpro.ca', ['Dashboard', 'Opportunities', 'Quotes', 'Jobs', 'More'], 'More',
     ['Calendar', 'Invoices', 'Payouts', 'Verified Pro Passport', 'Business profile', 'Plan', 'Team', 'My account', 'Notifications']),
]


def adb(*args, capture=True):
    return subprocess.run([ADB, *args], capture_output=capture, text=capture).stdout


def settle(seconds=1.5):
    time.sleep(seconds)


def nodes():
    adb('shell', 'uiautomator', 'dump', '/sdcard/tour.xml')
    raw = adb('exec-out', 'cat', '/sdcard/tour.xml')
    try:
        root = ET.fromstring(raw[raw.index('<?xml'):] if '<?xml' in raw else raw)
    except (ET.ParseError, ValueError):
        return []
    found = []
    for node in root.iter('node'):
        m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            found.append({'text': node.get('text', ''), 'desc': node.get('content-desc', ''),
                          'cls': node.get('class', ''), 'box': (x1, y1, x2, y2)})
    return found


def signature():
    return '|'.join(n['text'] for n in nodes() if n['text'])[:400]


def tap_node(node):
    x1, y1, x2, y2 = node['box']
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))


def find(label, last=False):
    matches = [n for n in nodes() if label in (n['text'], n['desc'])]
    if not matches:
        return None
    return matches[-1] if last else matches[0]


def tap(label, last=False):
    node = find(label, last)
    if node:
        tap_node(node)
        settle()
    return node is not None


def screen_size():
    m = re.search(r'(\d+)x(\d+)', adb('shell', 'wm', 'size'))
    return int(m.group(1)), int(m.group(2))


def swipe(up=True):
    w, h = screen_size()
    y1, y2 = (int(h * 0.75), int(h * 0.3)) if up else (int(h * 0.3), int(h * 0.75))
    adb('shell', 'input', 'swipe', str(w // 2), str(y1), str(w // 2), str(y2), '400')
    settle(1)


def snap(name):
    path = os.path.join(OUT, f'{ORIENTATION}-{name}.png')
    with open(path, 'wb') as fh:
        subprocess.run([ADB, 'exec-out', 'screencap', '-p'], stdout=fh)


def snap_with_scroll(name):
    snap(name)
    swipe(up=True)
    snap(f'{name}-scrolled')
    swipe(up=False)
    swipe(up=False)


def back():
    adb('shell', 'input', 'keyevent', '4')
    settle()


def open_tab(label):
    # The bottom bar on a phone, a rail on a large screen; nav items come
    # last in the tree, after any heading with the same words.
    if not tap(label, last=True):
        return False
    tap(label, last=True)  # a second tap pops the tab to its root
    return True


def open_and_return(node, name):
    before = signature()
    tap_node(node)
    settle(2)
    if signature() != before:
        snap_with_scroll(name)
        back()


def first_list_item(name):
    coded = [n for n in nodes() if re.match(r'^(Z|INV)-[A-Z0-9]{6}', n['text'])]
    if coded:
        open_and_return(coded[0], name)


def launch_fresh():
    # Wipe the app's data: no session, no half-typed form left over.
    adb('shell', 'pm', 'clear', 'com.zivett.app')
    adb('shell', 'monkey', '-p', 'com.zivett.app', '-c', 'android.intent.category.LAUNCHER', '1')
    settle(6)


def sign_in(email):
    # The way a person does it: Next from the email, ✓ from the password.
    launch_fresh()
    tap('Log in')
    fields = [n for n in nodes() if n['cls'].endswith('EditText')]
    if not fields:
        print(f'  no login form for {email}')
        return False
    tap_node(fields[0])
    settle(0.5)
    adb('shell', 'input', 'text', email)
    adb('shell', 'input', 'keyevent', '66')
    settle(1)
    adb('shell', 'input', 'text', 'password')
    adb('shell', 'input', 'keyevent', '66')
    settle(8)
    tap('Don’t allow')  # the first-launch notification prompt
    return True


def sign_out(menu_tab):
    open_tab(menu_tab)
    for _ in range(8):
        if tap('Log out'):
            settle(3)
            return
        swipe(up=True)


def tour(role, email, tabs, menu_tab, rows):
    print(f'{role}…')
    if not sign_in(email):
        return
    for tab in tabs:
        if not open_tab(tab):
            print(f'  no tab {tab}')
            continue
        name = f'{role}-{tab.lower()}'
        snap_with_scroll(name)
        if tab == menu_tab:
            for row in rows:
                for _ in range(4):
                    node = find(row)
                    if node:
                        open_and_return(node, f'{name}-{row.lower().replace(" ", "-")}')
                        break
                    swipe(up=True)
                open_tab(menu_tab)
        elif tab not in ('Book', 'Opportunities'):
            first_list_item(f'{name}-first')


def main():
    os.makedirs(OUT, exist_ok=True)
    # user_rotation counts from the display's NATURAL orientation: a
    # landscape-native tablet is landscape at 0 and portrait at 1.
    w, h = screen_size()
    natural_landscape = w > h
    wants_landscape = ORIENTATION == 'landscape'
    rotation = '0' if wants_landscape == natural_landscape else '1'
    adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
    adb('shell', 'settings', 'put', 'system', 'user_rotation', rotation)
    launch_fresh()
    snap('signedout-welcome')
    for role in ROLES:
        tour(*role)
    print('done')


if __name__ == '__main__':
    main()
