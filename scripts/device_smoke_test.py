#!/usr/bin/env python3
"""Walks through the release APK on a connected device or emulator.

With --after-v1, the version 1 walkthrough has just run on the same device:
installing this APK over it must keep Rahul's ₹1,000 Swiggy expense, his
₹400 repayment (₹600 still owed) and the Blinkit rule. The version 1 share
is then corrected from Data management and Rahul is settled, so the rest
runs from a clean balance.

Then: a ₹200 Swiggy card expense for Rahul (₹20 cashback, Rahul owes ₹180),
a ₹1,000 Swiggy expense split three ways with Amit (₹300 each), a ₹300
payment, an extra payment that leaves Rahul with ₹320 credit, the Share
Balance message, reports, dark theme, search and the Excel backup export.
Saves a screenshot of every screen and fails on a crash or a wrong amount.

Usage: device_smoke_test.py <apk> <screenshot-dir> [--after-v1]
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


def described_as(description):
    return lambda node: node.get("content-desc") == description


def text_field():
    return lambda node: node.get("class") == "android.widget.EditText"


def find(matches, timeout=10.0, index=0):
    """Returns the index-th node on screen that matches, waiting up to timeout seconds."""
    deadline = time.time() + timeout
    while True:
        found = [node for node in ui_nodes() if matches(node)]
        if len(found) > index:
            return found[index]
        if time.time() > deadline:
            return None
        time.sleep(0.5)


def scroll(down=True):
    size = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size")).groups()
    width, height = int(size[0]), int(size[1])
    start, end = (height * 3 // 5, height * 2 // 5) if down else (height * 2 // 5, height * 3 // 5)
    adb("shell", "input", "swipe", str(width // 2), str(start), str(width // 2), str(end), "400")
    time.sleep(1)


def find_scrolling(matches, what, scrolls=5, index=0):
    """Finds a node, scrolling down and then back up if it is off screen."""
    node = find(matches, timeout=5, index=index)
    for down in (True, False):
        for _ in range(scrolls if down else scrolls * 2):
            if node is not None:
                return node
            scroll(down)
            node = find(matches, timeout=1, index=index)
    if node is not None:
        return node
    fail(f"{what} not found on screen")


def tap(matches, what, scrolls=4, index=0):
    node = find_scrolling(matches, what, scrolls, index)
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(1)
    check_alive()


def type_text(text):
    adb("shell", "input", "text", text.replace(" ", "%s"))
    time.sleep(0.8)


def expect_id_text(resource_id, text):
    node = find_scrolling(by_id(resource_id), resource_id)
    if node.get("text") != text:
        fail(f"{resource_id} shows {node.get('text')!r}, expected {text!r}")
    print(f"ok  {resource_id} = {text}")


def expect_text(text):
    find_scrolling(with_text(text), repr(text))
    print(f"ok  {text!r} on screen")


def settle_rahul_from_v1():
    """Checks the upgraded version 1 data, corrects the version 1 share and settles Rahul."""
    expect_id_text("dashboard_to_receive", "₹600")
    tap(by_id("tab_udhaar"), "Udhaar tab")
    tap(by_id("person_Rahul"), "Rahul")
    expect_id_text("person_headline", "Rahul owes you ₹600")
    expect_text("Swiggy (expense share)")
    screenshot("02-v1-rahul-after-upgrade")
    tap(described_as("Back"), "Back button")

    tap(by_id("tab_settings"), "Settings tab")
    tap(by_id("settings_cashback_rules"), "Cashback rules")
    expect_text("Blinkit")
    screenshot("03-v1-rules-after-upgrade")
    tap(described_as("Back"), "Back button")

    # Version 1 charged Rahul the full ₹1,000; version 2 charges the ₹900 after cashback.
    tap(by_id("settings_data_management"), "Data management")
    expect_text("before cashback on 1 expense")
    screenshot("04-v1-review")
    tap(with_text("Review and apply corrections"), "Review button")
    tap(exact_text("Apply"), "Apply button")
    expect_text("Nothing to review")
    tap(described_as("Back"), "Back button")

    tap(by_id("tab_udhaar"), "Udhaar tab")
    tap(by_id("person_Rahul"), "Rahul")
    expect_id_text("person_headline", "Rahul owes you ₹500")
    tap(by_id("settle"), "Settle button")
    tap(by_id("settle_full"), "Full amount option")
    expect_text("Full settlement")
    tap(by_id("settle_confirm"), "Settle button")
    expect_id_text("person_headline", "Account settled ✓")
    tap(described_as("Back"), "Back button")
    tap(by_id("tab_home"), "Home tab")


def main():
    after_v1 = "--after-v1" in sys.argv[3:]
    # The emulator runs with a hardware keyboard; keep the on-screen keyboard
    # hidden so it never covers the controls being tapped. `input text` sends
    # key events, which Compose text fields accept without it.
    adb("shell", "settings", "put", "secure", "show_ime_with_hard_keyboard", "0")
    adb("install", "-r", APK)
    adb("logcat", "-c", check=False)
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
    if find(by_id("quick_expense"), timeout=30) is None:
        fail("the home screen did not appear")
    screenshot("01-home")

    if after_v1:
        settle_rahul_from_v1()
    else:
        tap(by_id("quick_person"), "Add person")
        tap(by_id("name_input"), "name field")
        type_text("Rahul")
        tap(by_id("save"), "Save button")
        expect_id_text("person_headline", "Account settled ✓")
        tap(described_as("Back"), "Back button")

    # ₹200 at Swiggy by card, entirely for Rahul: he owes the ₹180 after cashback.
    tap(by_id("quick_expense"), "Add expense")
    tap(by_id("amount_input"), "amount field")
    type_text("200")
    tap(by_id("merchant_input"), "merchant field")
    type_text("Swiggy")
    tap(by_id("split_add_people"), "Add people")
    tap(exact_text("Rahul"), "Rahul in the list")
    tap(exact_text("Done"), "Done")
    tap(by_id("split_include_me"), "Include my share switch")
    expect_id_text("breakdown_original", "₹200")
    expect_id_text("breakdown_cashback", "−₹20")
    expect_id_text("breakdown_effective", "₹180")
    expect_id_text("share_amount_Rahul", "₹180")
    screenshot("05-expense-for-rahul")
    tap(by_id("save_expense"), "Save button")
    expect_id_text("dashboard_to_receive", "₹180")

    # ₹1,000 split equally between me, Rahul and Amit: ₹300 each after cashback.
    tap(by_id("quick_expense"), "Add expense")
    tap(by_id("amount_input"), "amount field")
    type_text("1000")
    tap(by_id("merchant_input"), "merchant field")
    type_text("Swiggy")
    tap(by_id("split_add_people"), "Add people")
    tap(exact_text("Rahul"), "Rahul in the list")
    tap(exact_text("Add a new person"), "Add a new person")
    tap(text_field(), "name field")
    type_text("Amit")
    tap(exact_text("Add"), "Add button")
    expect_id_text("breakdown_effective", "₹900")
    expect_id_text("share_amount_Rahul", "₹300")
    expect_id_text("share_amount_Amit", "₹300")
    screenshot("06-split-three-ways")
    tap(by_id("save_expense"), "Save button")
    expect_id_text("dashboard_to_receive", "₹780")
    screenshot("07-home")

    tap(by_id("tab_expenses"), "Expenses tab")
    expect_text("Swiggy")
    screenshot("08-expenses")

    # Rahul: ₹180 + ₹300 = ₹480. He pays ₹300, then ₹500 more: ₹320 extra credit.
    tap(by_id("tab_udhaar"), "Udhaar tab")
    screenshot("09-udhaar")
    tap(by_id("person_Rahul"), "Rahul")
    expect_id_text("person_headline", "Rahul owes you ₹480")
    tap(by_id("record_payment"), "Record Payment")
    tap(by_id("amount_input"), "amount field")
    type_text("300")
    tap(by_id("save"), "Save button")
    expect_id_text("person_headline", "Rahul owes you ₹180")
    tap(by_id("settle"), "Settle button")
    tap(by_id("settle_partial"), "Another amount option")
    tap(by_id("settle_amount"), "amount field")
    type_text("500")
    expect_id_text("settle_preview", "Extra payment. After this: outstanding ₹0, Rahul has ₹320 credit.")
    screenshot("10-settle-extra")
    tap(by_id("settle_confirm"), "Settle button")
    expect_id_text("person_headline", "Rahul has ₹320 extra credit.")
    screenshot("11-rahul-credit")

    tap(by_id("share_balance"), "Share Balance")
    message = find_scrolling(by_id("share_message"), "share message")
    expected = "Hi Rahul, you've paid ₹320 extra. You currently have a ₹320 credit balance with me, which will be adjusted against your next expense."
    if message.get("text") != expected:
        fail(f"share message is {message.get('text')!r}")
    print("ok  share message offers the ₹320 credit wording")
    screenshot("12-share-balance")
    tap(described_as("Back"), "Back button")
    tap(described_as("Back"), "Back button")

    tap(by_id("tab_reports"), "Reports tab")
    expect_text("Net position")
    screenshot("13-reports")

    tap(by_id("tab_settings"), "Settings tab")
    tap(by_id("theme_setting"), "Theme")
    tap(exact_text("Dark"), "Dark")
    screenshot("14-settings-dark")
    tap(by_id("tab_home"), "Home tab")
    screenshot("15-home-dark")

    tap(described_as("Search"), "Search")
    tap(by_id("search_input"), "search field")
    type_text("Swiggy")
    expect_text("Total spending")
    screenshot("16-search")
    tap(described_as("Back"), "Back button")

    tap(by_id("tab_settings"), "Settings tab")
    tap(by_id("export_backup"), "Export full Excel backup")
    tap(exact_text("Share"), "Share button")
    time.sleep(2)
    screenshot("17-share-backup")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(1)
    expect_text("Backup ready to share.")
    screenshot("18-backup")

    check_alive()
    print("Device walkthrough passed")


if __name__ == "__main__":
    main()
