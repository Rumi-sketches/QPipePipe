"""Writes the GitHub Release notes for the current QPipePipe version.

    python tools/release/release_notes.py --apk-dir app/build/outputs/apk/release

The notes are qpipepipe_update_log (strings_qpipepipe.xml), a link to the PipePipe
release it is based on, install instructions, the signing certificate
(certificate-sha256.txt) and the SHA-256 of every APK in --apk-dir.
"""
import argparse
import hashlib
import re
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def upstream_version(build_gradle_text):
    return re.search(r'def appVersionName = "([^"]+)"', build_gradle_text).group(1)


def fork_release(qpipepipe_gradle_text):
    return int(re.search(r"(?m)^def qpipepipeRelease = (\d+)$", qpipepipe_gradle_text).group(1))


def update_log(strings_xml_text):
    """qpipepipe_update_log with Android string escapes turned into plain text."""
    for string in ElementTree.fromstring(strings_xml_text).iter("string"):
        if string.get("name") == "qpipepipe_update_log":
            text = string.text or ""
            return re.sub(r"\\(.)", lambda m: "\n" if m.group(1) == "n" else m.group(1), text)
    raise ValueError("qpipepipe_update_log not found")


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as file:
        for block in iter(lambda: file.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def notes(upstream, fork, log, certificate, apks):
    checksums = "\n".join(f"{sha256(apk)}  {apk.name}" for apk in apks)
    return f"""QPipePipe {upstream}-q{fork}, based on PipePipe {upstream}.

## What's new in QPipePipe

{log}

Everything else comes from [PipePipe {upstream}](https://github.com/InfinityLoop1308/PipePipe/releases/tag/v{upstream}).

## Install

Most phones need `arm64-v8a`; older 32-bit phones need `armeabi-v7a`. `x86` and `x86_64`
are for emulators and some tablets.

QPipePipe installs next to PipePipe. To move your subscriptions, playlists and history, use
Settings > Backup > Export database in PipePipe, then Import database in QPipePipe.

The APKs are signed with this certificate (`apksigner verify --print-certs <apk>`):

```
SHA-256: {certificate}
```

File checksums (SHA-256):

```
{checksums}
```
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--apk-dir", required=True, type=Path)
    args = parser.parse_args()
    apks = sorted(args.apk_dir.glob("*.apk"))
    if not apks:
        sys.exit(f"No APKs in {args.apk_dir}")
    sys.stdout.reconfigure(encoding="utf-8")
    print(notes(
        upstream_version((ROOT / "app" / "build.gradle").read_text(encoding="utf-8")),
        fork_release((ROOT / "app" / "qpipepipe.gradle").read_text(encoding="utf-8")),
        update_log((ROOT / "app/src/main/res/values/strings_qpipepipe.xml")
                   .read_text(encoding="utf-8")),
        (ROOT / "tools" / "release" / "certificate-sha256.txt").read_text().strip(),
        apks), end="")


if __name__ == "__main__":
    main()
