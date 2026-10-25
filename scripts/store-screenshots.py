#!/usr/bin/env python3
"""The Play Store screenshot set (2026-10-24, the tablet listing): the same
ten scenes, in the same order and names, as the phone set in
~/Desktop/ZiVETT Play Store Screenshots/Phone (1080x1920) — live tracking,
compare quotes, book a job, pro opportunities, invoice and pay, messages,
pro dashboard, pro calendar, create account, welcome.

    scripts/store-screenshots.py <out-dir>

Runs on the first adb device (a 1440x2560 tablet for the tablet slots),
against the local Sail server's seeded data, with the status bar in demo
mode (9:30, full battery) like the phone set. Read-only, like
screen-tour.py, whose helpers it uses.
"""

import importlib.util
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location('tour', os.path.join(HERE, 'screen-tour.py'))
tour = importlib.util.module_from_spec(spec)
sys.argv = [sys.argv[0], sys.argv[1] if len(sys.argv) > 1 else 'store', 'portrait']
spec.loader.exec_module(tour)
OUT = sys.argv[1]


def demo_status_bar():
    adb = tour.adb
    adb('shell', 'settings', 'put', 'global', 'sysui_demo_allowed', '1')
    for extras in (['-e', 'command', 'enter'],
                   ['-e', 'command', 'clock', '-e', 'hhmm', '0930'],
                   ['-e', 'command', 'battery', '-e', 'level', '100', '-e', 'plugged', 'false'],
                   ['-e', 'command', 'network', '-e', 'wifi', 'hide', '-e', 'mobile', 'hide'],
                   ['-e', 'command', 'notifications', '-e', 'visible', 'false']):
        adb('shell', 'am', 'broadcast', '-a', 'com.android.systemui.demo', *extras)


def launch_fresh():
    # The tour's fresh launch, plus the flag that hides the debug server
    # picker on the welcome screen.
    tour.adb('shell', 'pm', 'clear', 'com.zivett.app')
    tour.adb('shell', 'am', 'start', '-n', 'com.zivett.app/.MainActivity', '--ez', 'store_screenshots', 'true')
    tour.settle(6)


def sign_in(email):
    launch_fresh()
    tour.tap('Log in')
    fields = [n for n in tour.nodes() if n['cls'].endswith('EditText')]
    tour.tap_node(fields[0])
    tour.settle(0.5)
    tour.adb('shell', 'input', 'text', email)
    tour.adb('shell', 'input', 'keyevent', '66')
    tour.settle(1)
    tour.adb('shell', 'input', 'text', 'password')
    tour.adb('shell', 'input', 'keyevent', '66')
    tour.settle(8)
    tour.tap('Don’t allow')


def shot(name):
    tour.settle(2)
    with open(os.path.join(OUT, f'{name}.png'), 'wb') as fh:
        import subprocess
        subprocess.run([tour.ADB, 'exec-out', 'screencap', '-p'], stdout=fh)


def open_text(text):
    node = next((n for n in tour.nodes() if text in n['text']), None)
    if node is None:
        print(f'  not found: {text}')
        return False
    tour.tap_node(node)
    tour.settle(3)
    return True


def main():
    os.makedirs(OUT, exist_ok=True)
    tour.adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
    tour.adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
    demo_status_bar()

    sign_in('customer@ratedpro.ca')
    tour.open_tab('Home')
    shot('01-live-tracking')
    if open_text('Invoice due'):
        shot('05-invoice-and-pay')
        tour.back()
    tour.open_tab('Jobs')
    if open_text('Repaint spare bedroom'):
        shot('02-compare-quotes')
        tour.back()
    tour.open_tab('Messages')
    if open_text('Great, thank you'):
        shot('06-messages')
        tour.back()
    tour.open_tab('Book')
    shot('03-book-a-job')

    sign_in('company@ratedpro.ca')
    tour.open_tab('Dashboard')
    shot('07-pro-dashboard')
    tour.open_tab('Opportunities')
    shot('04-pro-opportunities')
    tour.open_tab('More')
    if open_text('Calendar'):
        shot('08-pro-calendar')
        tour.back()

    launch_fresh()
    shot('10-welcome')
    tour.tap('Create an account')
    shot('09-create-account')
    print('done')


if __name__ == '__main__':
    main()
