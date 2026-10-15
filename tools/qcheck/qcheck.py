"""Regression check for QPipePipe on a real phone over adb.

Runs the fork feature checks listed in FORK.md, measures app and video start
times and looks for crashes. It needs a debuggable build (it reads and writes
the app preferences with run-as, or through the debug build's
DebugPreferencesReceiver where the ROM blocks run-as) and an unlocked phone. The user's preferences
are saved first and restored at the end; watch and search history are turned
off while the checks run.

Usage:
    python tools/qcheck/qcheck.py [--apk PATH] [--package ID] [--baseline FILE]

With more than one phone connected, pick one with ANDROID_SERIAL=<serial> (see
"adb devices"). Start times depend on the phone: compare only with a baseline
from the same model, which the result file records.

Results are printed and written to tools/qcheck/results/<timestamp>.json; keep
the file of each release as the baseline for the next one. With --baseline
(an earlier result file) start times are compared against it. A copy of the
user's preferences is kept in build/qcheck/ during the run (never commit it).
"""
import argparse
import base64
import json
import os
import re
import statistics
import subprocess
import sys
import time
from pathlib import Path

os.environ["MSYS_NO_PATHCONV"] = "1"  # keep Git Bash from rewriting /sdcard paths
sys.stdout.reconfigure(encoding="utf-8")

PACKAGE = "io.github.rumisketches.qpipepipe.debug"
MAIN_ACTIVITY = "org.schabi.newpipe.MainActivity"
SEARCH_QUERY = "nasa"
# Short, long-lived public videos, unlikely to disappear.
VIDEOS = ["jNQXAC9IVRw", "aqz-KE-bpKQ", "dQw4w9WgXcQ"]
COLD_START_RUNS = 10
VIDEO_START_RUNS = 5
PLAY_TIMEOUT = 25
REGRESSION_THRESHOLD = 0.15

# Preferences forced during the run so results don't depend on the user's setup.
BASE_PREFS = {
    "enable_watch_history": False,
    "enable_search_history": False,
    "enable_playback_resume": False,
    "autoplay_key": "autoplay_always_key",
    # Video links open the player directly; the default asks how to open them.
    "preferred_open_action_key": "video_player",
    "tap_always_plays_key": True,
    "clear_queue_confirmation_key": False,
    "enable_swipe_actions_key": False,
    # The checks look for English labels ("Enqueue", "Play now").
    "app_language_key": "en",
    # Startup dialogs cover the main screen and hide it from uiautomator: mark the
    # announcements and this version's What's New as already seen.
    "last_announcement_id": "991231",
    "qpipepipe_last_announcement_id": "991231",
    "version_code": 2_000_000_000,
}


# --- adb -------------------------------------------------------------------

def adb(*args, stdin=None):
    result = subprocess.run(["adb", *args], input=stdin, capture_output=True,
                            text=True, encoding="utf-8", errors="replace")
    return result.stdout


def shell(*args):
    return adb("shell", *args)


def check_device():
    if "\tdevice" not in adb("devices"):
        sys.exit("No phone connected over adb.")
    shell("input", "keyevent", "KEYCODE_WAKEUP")
    if "isKeyguardShowing=true" in shell("dumpsys", "window"):
        sys.exit("The phone is locked: unlock it and keep the screen on.")
    if PACKAGE not in shell("pm", "list", "packages", PACKAGE):
        sys.exit(f"{PACKAGE} is not installed.")


SCREEN_OFF_TIMEOUT_MS = "1800000"


def keep_screen_on():
    """Raises the screen timeout to 30 minutes for the run; returns the value to restore.

    When the screen turns off the phone locks and the system freezes the app, so every
    later check fails. "Stay awake while charging" is not enough: some phones ignore it.
    """
    previous = shell("settings", "get", "system", "screen_off_timeout").strip()
    shell("settings", "put", "system", "screen_off_timeout", SCREEN_OFF_TIMEOUT_MS)
    return previous if previous.isdigit() else "60000"


def force_stop():
    shell("am", "force-stop", PACKAGE)


def launch():
    force_stop()
    shell("am", "start", "-W", "-n", f"{PACKAGE}/{MAIN_ACTIVITY}")
    time.sleep(2)


# --- preferences ------------------------------------------------------------

PREFS_FILE = f"shared_prefs/{PACKAGE}_preferences.xml"


PREFS_RECEIVER = "org.schabi.newpipe.qpipepipe.DebugPreferencesReceiver"
USE_RUN_AS = True  # set by detect_prefs_access()


def detect_prefs_access():
    """Some custom ROMs block run-as with SELinux; the debug build has a receiver for that."""
    global USE_RUN_AS
    USE_RUN_AS = "uid=" in shell("run-as", PACKAGE, "id")
    if not USE_RUN_AS:
        print("run-as is blocked on this phone: using the debug preferences receiver")


def prefs_broadcast(*extras):
    out = shell("am", "broadcast", "-n", f"{PACKAGE}/{PREFS_RECEIVER}", *extras)
    match = re.search(r'data="([^"]*)"', out)
    return match.group(1) if match else ""


def read_prefs():
    if USE_RUN_AS:
        return shell("run-as", PACKAGE, "cat", PREFS_FILE)
    data = prefs_broadcast("--es", "op", "read")
    return "" if data.startswith("error") else base64.b64decode(data).decode("utf-8")


def write_prefs(xml):
    force_stop()
    if USE_RUN_AS:
        adb("shell", f"run-as {PACKAGE} sh -c 'cat > {PREFS_FILE}'", stdin=xml)
        return
    encoded = base64.b64encode(xml.encode("utf-8")).decode("ascii")
    if prefs_broadcast("--es", "op", "write", "--es", "xml", encoded) != "ok":
        raise RuntimeError("the debug preferences receiver could not write the preferences")
    force_stop()


def set_prefs(values):
    xml = read_prefs()
    for key, value in values.items():
        xml = re.sub(rf'\s*<(boolean|string|int) name="{re.escape(key)}"[^>]*?(/>|>.*?</string>)',
                     "", xml, flags=re.S)
        if isinstance(value, bool):
            entry = f'<boolean name="{key}" value="{str(value).lower()}" />'
        elif isinstance(value, int):
            entry = f'<int name="{key}" value="{value}" />'
        else:
            entry = f'<string name="{key}">{value}</string>'
        xml = xml.replace("</map>", f"    {entry}\n</map>")
    write_prefs(xml)


def pref_values(xml, keys):
    found = {}
    for key in keys:
        m = re.search(rf'<boolean name="{re.escape(key)}" value="(\w+)"', xml)
        m = m or re.search(rf'<string name="{re.escape(key)}">([^<]*)<', xml)
        found[key] = m.group(1) if m else None
    return found


# --- UI ---------------------------------------------------------------------

def ui_nodes():
    shell("uiautomator", "dump", "/sdcard/qcheck-ui.xml")
    xml = shell("cat", "/sdcard/qcheck-ui.xml")
    nodes = []
    for raw in re.findall(r"<node [^>]*>", xml):
        attr = dict(re.findall(r'([\w-]+)="([^"]*)"', raw))
        bounds = [int(n) for n in re.findall(r"\d+", attr.get("bounds", ""))]
        if len(bounds) == 4:
            attr["box"] = bounds
            attr["x"] = (bounds[0] + bounds[2]) // 2
            attr["y"] = (bounds[1] + bounds[3]) // 2
            nodes.append(attr)
    return nodes


def find(nodes, res_id=None, text=None):
    for node in nodes:
        if res_id and node.get("resource-id", "").endswith("/" + res_id):
            return node
        if text and (node.get("text") == text or node.get("content-desc") == text):
            return node
    return None


def wait_for(res_id=None, text=None, timeout=15):
    deadline = time.time() + timeout
    while time.time() < deadline:
        node = find(ui_nodes(), res_id, text)
        if node:
            return node
        time.sleep(0.5)
    return None


def tap(node):
    shell("input", "tap", str(node["x"]), str(node["y"]))
    time.sleep(1.5)


def back():
    shell("input", "keyevent", "KEYCODE_BACK")
    time.sleep(1.5)


def stream_rows():
    return [n for n in ui_nodes() if n.get("resource-id", "").endswith("/itemVideoTitleView")]


def search(query=SEARCH_QUERY):
    button = wait_for(res_id="action_search")
    if not button:
        raise CheckError("search button not found")
    tap(button)
    shell("input", "text", query.replace(" ", "%s"))
    shell("input", "keyevent", "KEYCODE_ENTER")
    if not wait_for(res_id="itemVideoTitleView", timeout=20):
        raise CheckError("no search results")
    time.sleep(1)


def open_row(index):
    rows = stream_rows()
    if len(rows) <= index:
        raise CheckError(f"search result {index} not visible")
    tap(rows[index])


# --- media session -----------------------------------------------------------

def sessions():
    out = shell("dumpsys", "media_session")
    blocks = out.split("MediaSessionManager ")
    found = []
    for block in blocks:
        if f"package={PACKAGE}" not in block:
            continue
        state = re.search(r"state=PlaybackState \{state=(\w+)\((\d+)\)", block)
        size = re.search(r"queueTitle=\S*, size=(\d+)", block)
        found.append({"state": int(state.group(2)) if state else None,
                      "queue": int(size.group(1)) if size else 0})
    return found


def is_playing():
    return any(s["state"] == 3 for s in sessions())


def queue_size():
    return max((s["queue"] for s in sessions()), default=0)


def wait_playing(timeout=PLAY_TIMEOUT):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if is_playing():
            return True
        time.sleep(0.3)
    return False


def wait_queue(expected, timeout=10):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if queue_size() == expected:
            return True
        time.sleep(0.5)
    return False


# --- checks -----------------------------------------------------------------

class CheckError(Exception):
    pass


def check_tap_plays(enabled):
    """FORK.md "Tapping a video always plays it": with Autoplay "Never"."""
    set_prefs({"autoplay_key": "autoplay_never_key", "tap_always_plays_key": enabled})
    launch()
    search()
    open_row(0)
    return wait_playing(timeout=PLAY_TIMEOUT if enabled else 12)


def start_queue_of_two():
    """Plays the first search result and enqueues the second one (long-press menu)."""
    launch()
    search()
    open_row(0)
    if not wait_playing():
        raise CheckError("first video did not start")
    back()  # collapse the player to the mini player
    row = stream_rows()[1]
    shell("input", "swipe", str(row["x"]), str(row["y"]), str(row["x"]), str(row["y"]), "900")
    item = wait_for(text="Enqueue", timeout=5)
    if not item:
        raise CheckError("long-press menu has no Enqueue")
    tap(item)
    if not wait_queue(2):
        raise CheckError(f"queue is {queue_size()} after enqueue, expected 2")


def check_clear_queue_dialog(enabled):
    """FORK.md "Clear-queue confirmation": dialog appears and Enqueue keeps the queue."""
    set_prefs({"autoplay_key": "autoplay_always_key", "clear_queue_confirmation_key": enabled})
    start_queue_of_two()
    open_row(2)
    dialog = wait_for(text="Play now", timeout=5)
    if not dialog:
        return False
    enqueue = find(ui_nodes(), text="Enqueue")
    if not enqueue:
        raise CheckError("dialog has no Enqueue choice")
    tap(enqueue)
    return wait_queue(3)


def check_swipe_enqueue(enabled):
    """FORK.md "Swipe actions": swiping left with Enqueue grows the queue by one."""
    set_prefs({"autoplay_key": "autoplay_always_key", "enable_swipe_actions_key": enabled,
               "swipe_left_action_key": "ENQUEUE"})
    launch()
    search()
    open_row(0)
    if not wait_playing():
        raise CheckError("first video did not start")
    back()
    before = queue_size()
    row = stream_rows()[1]
    width = int(re.search(r"(\d+)x\d+", shell("wm", "size")).group(1))
    # Stay away from the screen edges: edge swipes are the system back gesture.
    shell("input", "swipe", str(width * 85 // 100), str(row["y"]),
          str(width * 15 // 100), str(row["y"]), "300")
    grew = wait_queue(before + 1, timeout=8)
    time.sleep(1.5)
    rows = stream_rows()
    back_in_place = len(rows) > 1 and abs(rows[1]["box"][0] - row["box"][0]) < 10
    return grew and back_in_place


FEATURE_CHECKS = [
    ("tap always plays", check_tap_plays),
    ("clear-queue dialog", check_clear_queue_dialog),
    ("swipe enqueue", check_swipe_enqueue),
]


def run_feature_check(name, check):
    """Runs a check with the feature on and off. Off must fail, or the check proves nothing."""
    outcome = {}
    for enabled in (True, False):
        set_prefs(BASE_PREFS)
        try:
            outcome["on" if enabled else "off"] = check(enabled)
        except CheckError as e:
            outcome["on" if enabled else "off"] = f"error: {e}"
    if outcome["on"] is True and outcome["off"] is False:
        verdict = "PASS"
    elif outcome["on"] is True:
        verdict = "INSENSITIVE"  # passes with the feature off too
    elif isinstance(outcome["on"], str):
        verdict = "ERROR"
    else:
        verdict = "FAIL"
    print(f"  {name:22} {verdict:12} on={outcome['on']} off={outcome['off']}")
    return {"name": name, "verdict": verdict, **outcome}


# --- timings ----------------------------------------------------------------

def cold_start_ms():
    force_stop()
    time.sleep(1.5)
    out = shell("am", "start", "-W", "-n", f"{PACKAGE}/{MAIN_ACTIVITY}")
    m = re.search(r"TotalTime: (\d+)", out)
    return int(m.group(1)) if m else None


def video_start_ms(video_id):
    force_stop()
    time.sleep(1.5)
    t0 = time.time()
    shell("am", "start", "-a", "android.intent.action.VIEW",
          "-d", f"https://www.youtube.com/watch?v={video_id}", "-p", PACKAGE)
    return round((time.time() - t0) * 1000) if wait_playing() else None


def summarize(samples):
    ok = [s for s in samples if s is not None]
    return {"median_ms": statistics.median(ok) if ok else None,
            "min_ms": min(ok, default=None), "max_ms": max(ok, default=None),
            "failed": len(samples) - len(ok), "samples": samples}


def measure_timings():
    set_prefs(BASE_PREFS)
    timings = {"cold_start": summarize([cold_start_ms() for _ in range(COLD_START_RUNS)])}
    for video in VIDEOS:
        timings[f"video_{video}"] = summarize([video_start_ms(video) for _ in range(VIDEO_START_RUNS)])
    force_stop()
    for name, t in timings.items():
        print(f"  {name:22} median {t['median_ms']} ms (min {t['min_ms']}, max {t['max_ms']},"
              f" failed {t['failed']})")
    return timings


def compare(timings, baseline_file, device):
    baseline_result = json.loads(Path(baseline_file).read_text(encoding="utf-8"))
    baseline = baseline_result["timings"]
    regressions = []
    if baseline_result.get("device", device) != device:
        regressions.append(f"baseline is from {baseline_result['device']}, not {device}:"
                           " start times are not comparable")
    for name, t in timings.items():
        if t["failed"]:
            regressions.append(f"{name}: {t['failed']} of {len(t['samples'])} starts failed")
        old = baseline.get(name, {}).get("median_ms")
        if old and t["median_ms"] and t["median_ms"] > old * (1 + REGRESSION_THRESHOLD):
            regressions.append(f"{name}: {old} -> {t['median_ms']} ms")
    return regressions


# --- main -------------------------------------------------------------------

def main():
    global PACKAGE, PREFS_FILE
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--apk", help="install this APK over the current one first")
    parser.add_argument("--package", default=PACKAGE)
    parser.add_argument("--baseline", help="earlier result file to compare timings with")
    parser.add_argument("--skip-timings", action="store_true")
    args = parser.parse_args()
    PACKAGE = args.package
    PREFS_FILE = f"shared_prefs/{PACKAGE}_preferences.xml"

    check_device()
    detect_prefs_access()
    user_prefs = read_prefs()
    if "<map" not in user_prefs:
        sys.exit("Cannot read the app preferences: is this a debuggable build?")
    stamp = time.strftime("%Y%m%d-%H%M%S")
    backup_dir = Path(__file__).resolve().parents[2] / "build" / "qcheck"
    backup_dir.mkdir(parents=True, exist_ok=True)
    # Kept on disk in case the run is killed before the finally block restores them.
    (backup_dir / f"prefs-backup-{stamp}.xml").write_text(user_prefs, encoding="utf-8")
    fork_keys = ["tap_always_plays_key", "clear_queue_confirmation_key",
                 "enable_swipe_actions_key", "swipe_left_action_key", "swipe_right_action_key"]
    device = shell("getprop", "ro.product.model").strip()
    print(f"Phone: {device}, Android {shell('getprop', 'ro.build.version.release').strip()}")
    result = {"package": PACKAGE, "device": device,
              "started": time.strftime("%Y-%m-%d %H:%M:%S")}
    adb("logcat", "-b", "crash", "-c")
    screen_timeout = keep_screen_on()
    try:
        if args.apk:
            print("Upgrade install")
            before = pref_values(user_prefs, fork_keys)
            out = adb("install", "-r", args.apk)
            launch()
            after = pref_values(read_prefs(), fork_keys)
            ok = "Success" in out and before == after
            result["upgrade"] = {"ok": ok, "install": out.strip().splitlines()[-1:],
                                 "before": before, "after": after}
            print(f"  {'upgrade keeps settings':22} {'PASS' if ok else 'FAIL'}")
        result["version"] = re.findall(r"versionName=(\S+)", shell("dumpsys", "package", PACKAGE))[:1]
        print("Feature checks")
        result["features"] = [run_feature_check(n, c) for n, c in FEATURE_CHECKS]
        if not args.skip_timings:
            print("Start times")
            result["timings"] = measure_timings()
            if args.baseline:
                result["regressions"] = compare(result["timings"], args.baseline, device)
                print("Regressions over threshold:", result["regressions"] or "none")
    finally:
        write_prefs(user_prefs)
        shell("settings", "put", "system", "screen_off_timeout", screen_timeout)
        crashes = adb("logcat", "-b", "crash", "-d")
        result["crashes"] = [l for l in crashes.splitlines() if PACKAGE in l or "FATAL" in l]
        print(f"Crashes: {len(result['crashes'])}")
        out_dir = Path(__file__).resolve().parent / "results"
        out_dir.mkdir(exist_ok=True)
        out_file = out_dir / f"{stamp}.json"
        out_file.write_text(json.dumps(result, indent=2), encoding="utf-8")
        print(f"Results: {out_file}")
        print("User preferences and screen timeout restored.")


if __name__ == "__main__":
    main()
