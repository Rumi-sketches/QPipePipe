"""Looks for a new stable PipePipe release and merges it into a sync/vX.Y.Z branch.

Run by .github/workflows/upstream-watcher.yml; it also works from a local clone:

    python tools/upstream/check_upstream.py --dry-run

Steps (FORK.md, "Upstream watcher"):
1. Compare PipePipe's latest stable release with the version QPipePipe is based on
   (appVersionName in app/build.gradle, which upstream bumps itself).
2. Stop if sync/vX.Y.Z already exists on origin: that release is being handled.
3. Read the client and extractor commits pinned by the release in InfinityLoop1308/PipePipe.
4. Create sync/vX.Y.Z from the base branch and merge the pinned client commit.
   - Clean merge: update tools/upstream/pins.properties, reset qpipepipeRelease to 1
     and commit.
   - Conflicts: abort the merge, leave the branch at the base for manual work.
5. Push the branch (unless --dry-run or --no-push).

After resolving conflicts by hand, update the pins with:

    python tools/upstream/check_upstream.py --write-pins vX.Y.Z

Results go to stdout and, in GitHub Actions, to $GITHUB_OUTPUT: status (up-to-date,
exists, clean, conflict), version, branch, client_sha, extractor_sha, conflicts.
"""
import argparse
import json
import os
import re
import subprocess
import sys
import urllib.request
from pathlib import Path

UMBRELLA_REPO = "InfinityLoop1308/PipePipe"
CLIENT_URL = "https://github.com/InfinityLoop1308/PipePipeClient.git"
STABLE_TAG = re.compile(r"^v(\d+)\.(\d+)\.(\d+)$")
ROOT = Path(__file__).resolve().parents[2]
PINS = ROOT / "tools" / "upstream" / "pins.properties"


def parse_version(text):
    """Returns (major, minor, patch) for "5.4.0" or "v5.4.0", None for anything else."""
    match = STABLE_TAG.match(text if text.startswith("v") else "v" + text)
    return tuple(int(part) for part in match.groups()) if match else None


def based_on_version(build_gradle_text):
    match = re.search(r'def appVersionName = "([^"]+)"', build_gradle_text)
    if not match:
        raise ValueError("appVersionName not found in app/build.gradle")
    return match.group(1)


def reset_fork_release(qpipepipe_gradle_text):
    """Sets qpipepipeRelease to 1; returns the new text (unchanged if already 1)."""
    return re.sub(r"(?m)^def qpipepipeRelease = \d+$", "def qpipepipeRelease = 1",
                  qpipepipe_gradle_text)


def write_pins(text, version, client_sha, extractor_sha):
    """Returns pins.properties with the given release; comment lines are kept."""
    values = {"version": version, "client": client_sha, "extractor": extractor_sha}
    lines = [line for line in text.splitlines() if line.startswith("#")]
    lines += [f"{key}={value}" for key, value in values.items()]
    return "\n".join(lines) + "\n"


def release_pins(tag):
    client = github_api(f"repos/{UMBRELLA_REPO}/contents/PipePipeClient?ref={tag}")["sha"]
    extractor = github_api(f"repos/{UMBRELLA_REPO}/contents/PipePipeExtractor?ref={tag}")["sha"]
    return client, extractor


def update_pins_file(version, client_sha, extractor_sha):
    text = PINS.read_text(encoding="utf-8") if PINS.exists() else ""
    PINS.write_text(write_pins(text, version, client_sha, extractor_sha), encoding="utf-8")


def github_api(path):
    request = urllib.request.Request(f"https://api.github.com/{path}",
                                     headers={"Accept": "application/vnd.github+json"})
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def git(*args, check=True):
    result = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True)
    if check and result.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed:\n{result.stderr.strip()}")
    return result


def output(**values):
    for key, value in values.items():
        print(f"{key}: {value}")
    path = os.environ.get("GITHUB_OUTPUT")
    if path:
        with open(path, "a", encoding="utf-8") as out:
            for key, value in values.items():
                out.write(f"{key}={value}\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--based-on", help="pretend QPipePipe is based on this version")
    parser.add_argument("--base", default="origin/dev", help="ref the sync branch starts from")
    parser.add_argument("--dry-run", action="store_true",
                        help="only report what would happen, change nothing")
    parser.add_argument("--no-push", action="store_true",
                        help="create and merge the branch locally but do not push it")
    parser.add_argument("--write-pins", metavar="TAG",
                        help="only write pins.properties for this release (e.g. v5.5.0)")
    args = parser.parse_args()

    if args.write_pins:
        if parse_version(args.write_pins) is None:
            sys.exit(f"Not a stable release tag: {args.write_pins}")
        client_sha, extractor_sha = release_pins(args.write_pins)
        update_pins_file(args.write_pins.lstrip("v"), client_sha, extractor_sha)
        print(f"{PINS.relative_to(ROOT)}: {args.write_pins} client {client_sha}"
              f" extractor {extractor_sha}")
        return

    current = args.based_on or based_on_version(
        (ROOT / "app" / "build.gradle").read_text(encoding="utf-8"))
    latest = github_api(f"repos/{UMBRELLA_REPO}/releases/latest")["tag_name"]
    if parse_version(latest) is None or parse_version(current) is None:
        sys.exit(f"Unexpected version format: latest {latest!r}, based on {current!r}")
    print(f"QPipePipe is based on {current}; latest stable PipePipe is {latest}")
    if parse_version(latest) <= parse_version(current):
        output(status="up-to-date", version=latest[1:])
        return

    version = latest[1:]
    branch = f"sync/v{version}"
    if git("ls-remote", "--heads", "origin", branch).stdout.strip():
        output(status="exists", version=version, branch=branch)
        return

    client_sha, extractor_sha = release_pins(latest)
    print(f"{latest} pins client {client_sha} and extractor {extractor_sha}")
    if args.dry_run:
        output(status="would-merge", version=version, branch=branch,
               client_sha=client_sha, extractor_sha=extractor_sha)
        return

    git("fetch", "--quiet", CLIENT_URL, client_sha)
    git("checkout", "--quiet", "-B", branch, args.base)
    merge = git("merge", "--no-ff", "--no-edit", "-m", f"Merge upstream {latest} into dev",
                client_sha, check=False)
    if merge.returncode == 0:
        status, conflicts = "clean", ""
        gradle = ROOT / "app" / "qpipepipe.gradle"
        text = gradle.read_text(encoding="utf-8") if gradle.exists() else ""
        if reset_fork_release(text) != text:
            gradle.write_text(reset_fork_release(text), encoding="utf-8")
        update_pins_file(version, client_sha, extractor_sha)
        git("add", "--", *[str(path) for path in (PINS, gradle) if path.exists()])
        if git("diff", "--cached", "--quiet", check=False).returncode != 0:
            git("commit", "--quiet", "-m",
                f"chore(upstream): pin {latest} and reset the QPipePipe release number")
    else:
        conflicted = git("diff", "--name-only", "--diff-filter=U").stdout.split()
        if not conflicted:
            raise RuntimeError(f"Merge failed without conflicts:\n{merge.stdout}{merge.stderr}")
        git("merge", "--abort")
        status, conflicts = "conflict", " ".join(conflicted)
        print("Conflicts:\n  " + "\n  ".join(conflicted))
    if not args.no_push:
        git("push", "--quiet", "origin", f"{branch}:{branch}")
    output(status=status, version=version, branch=branch, client_sha=client_sha,
           extractor_sha=extractor_sha, conflicts=conflicts)


if __name__ == "__main__":
    main()
