# deb_pair.py PIN OUT.json -- pair a headless browser with Debian LAN Mode and save its storage.
import os
import sys
from playwright.sync_api import sync_playwright
with sync_playwright() as p:
    b = p.chromium.launch(); ctx = b.new_context(viewport={'width': 1100, 'height': 600}); pg = ctx.new_page()
    pg.goto('http://' + os.environ.get('PHONE', '192.168.1.103') + ':7682/'); pg.wait_for_selector('#pin', state='visible', timeout=15000)
    pg.fill('#pin', sys.argv[1]); pg.click('#pair-form button'); pg.wait_for_selector('#state.ok', timeout=30000)
    ctx.storage_state(path=sys.argv[2]); print('paired'); b.close()
