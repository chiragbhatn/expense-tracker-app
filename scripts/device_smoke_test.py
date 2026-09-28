#!/usr/bin/env python3
"""Walks through the release APK on a connected device or emulator.

Installs the APK, records a Swiggy card payment of ₹1,000 made for Rahul, and
checks that the app shows ₹100 cashback, a ₹900 effective expense and ₹1,000
owed by Rahul. Saves a screenshot of every screen along the way and fails on a
crash or a wrong amount.

Usage: device_smoke_test.py <apk> <screenshot-dir>
"""

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "io.github.chiragbhatn.expensetracker"
APK = sys.argv[1]
OUT = Path(sys.argv[2])


def adb(*args, check=True):
    return subprocess.run(["adb", *args], check=check, capture_output=True, text=True).stdout


def screenshot(name):
    OUT.mkdir(parents=True, exist_ok=True)
    png = subprocess.run(["adb", "exec-out", "screencap", "-p"], check=True, capture_output=True).stdout
    (OUT / f"{name}.png").write_bytes(png)
    # Also log what is on screen, so the walkthrough can be reviewed from the job log.
    print(f"--- screenshot {name} ---")
    for node in ui_nodes():
        text = node.get("text") or node.get("content-desc")
        resource_id = node.get("resource-id")
        if text or resource_id:
            print(f"    {node.get('bounds'):<26} {resource_id or '':<26} {text!r}")


def fail(message):
    screenshot("failure")
    print(adb("logcat", "-d", "-b", "crash", check=False))
    sys.exit(f"FAILED: {message}")


def check_alive():
    if not adb("shell", "pidof", PACKAGE, check=False).strip():
        fail("the app is no longer running (crash?)")


def ui_nodes():
    for _ in range(5):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False)
        xml = adb("shell", "cat", "/sdcard/ui.xml", check=False)
        if xml.lstrip().startswith("<?xml"):
            return list(ET.fromstring(xml).iter("node"))
        time.sleep(1)
    print("warning: could not read the screen with uiautomator")
    return []


def label(node):
    return f"{node.get('text', '')} {node.get('content-desc', '')}"


# Compose test tags are exposed as resource ids (testTagsAsResourceId). Dialogs are
# separate windows without that setting, so they are matched by text or class.
def by_id(resource_id):
    return lambda node: node.get("resource-id") == resource_id


def with_text(text):
    return lambda node: text in label(node)


def exact_text(text):
    return lambda node: node.get("text") == text


def text_field():
    return lambda node: node.get("class") == "android.widget.EditText"


def find(matches, timeout=10.0):
    deadline = time.time() + timeout
    while True:
        for node in ui_nodes():
            if matches(node):
                return node
        if time.time() > deadline:
            return None
        time.sleep(0.5)


def scroll_down():
    size = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size")).groups()
    width, height = int(size[0]), int(size[1])
    adb("shell", "input", "swipe", str(width // 2), str(height * 2 // 3), str(width // 2), str(height // 3), "400")
    time.sleep(1)


def find_scrolling(matches, what, scrolls=4):
    for attempt in range(scrolls + 1):
        node = find(matches, timeout=3 if attempt < scrolls else 5)
        if node is not None:
            return node
        scroll_down()
    fail(f"{what} not found on screen")


def tap(matches, what, scrolls=4):
    node = find_scrolling(matches, what, scrolls)
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(1)
    check_alive()


def type_text(text):
    adb("shell", "input", "text", text.replace(" ", "%s"))
    time.sleep(0.8)


def hide_keyboard():
    if "mInputShown=true" in adb("shell", "dumpsys", "input_method"):
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(1)


def expect_id_text(resource_id, text):
    node = find_scrolling(by_id(resource_id), resource_id)
    if node.get("text") != text:
        fail(f"{resource_id} shows {node.get('text')!r}, expected {text!r}")
    print(f"ok  {resource_id} = {text}")


def expect_text(text):
    find_scrolling(with_text(text), repr(text))
    print(f"ok  {text!r} on screen")


def main():
    adb("install", "-r", APK)
    adb("logcat", "-c", check=False)
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
    if find(by_id("add_expense"), timeout=30) is None:
        fail("the dashboard did not appear")
    screenshot("01-dashboard-empty")

    # Swiggy, card, ₹1,000, paid for Rahul.
    tap(by_id("add_expense"), "Add expense button")
    tap(by_id("amount_input"), "amount field")
    type_text("1000")
    tap(by_id("merchant_input"), "merchant field")
    type_text("Swiggy")
    hide_keyboard()
    tap(by_id("add_person"), "Add person chip")
    tap(text_field(), "name field")
    type_text("Rahul")
    tap(exact_text("Add"), "Add button")
    hide_keyboard()

    expect_id_text("breakdown_original", "₹1,000")
    expect_id_text("breakdown_cashback", "−₹100")
    expect_id_text("breakdown_effective", "₹900")
    expect_id_text("breakdown_udhaar", "₹1,000")
    screenshot("02-add-expense-breakdown")

    tap(by_id("save_expense"), "Save button")
    expect_id_text("dashboard_card_spending", "₹1,000")
    expect_id_text("dashboard_cashback", "₹100")
    expect_id_text("dashboard_effective", "₹900")
    expect_text("Money to receive")
    screenshot("03-dashboard")

    tap(by_id("tab_expenses"), "Expenses tab")
    expect_text("Swiggy")
    screenshot("04-expenses")

    tap(by_id("tab_udhaar"), "Udhaar tab")
    expect_text("Rahul")
    screenshot("05-udhaar")
    tap(with_text("Rahul"), "Rahul")
    expect_id_text("person_balance", "₹1,000")
    screenshot("06-rahul")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(1)

    tap(by_id("tab_rules"), "Cashback tab")
    expect_text("Swiggy")
    screenshot("07-cashback-rules")
    tap(by_id("add_rule"), "Add merchant button")
    screenshot("08-add-rule-dialog")

    check_alive()
    print("Device walkthrough passed")


if __name__ == "__main__":
    main()
