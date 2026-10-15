# QPipePipe fork notes

This file is the reference for anyone merging upstream PipePipe into this fork,
human or AI. It lists what the fork changes, which behaviour must survive a
merge, and how conflicts are resolved.

## Upstream

- Client: https://github.com/InfinityLoop1308/PipePipeClient (branch `dev`)
- Extractor: https://github.com/InfinityLoop1308/PipePipeExtractor (no fork patches)
- Releases: https://github.com/InfinityLoop1308/PipePipe

The client and the extractor are separate repositories built together
(`includeBuild('../PipePipeExtractor')` in `settings.gradle`). The umbrella
repository `InfinityLoop1308/PipePipe` pins both as submodules for every
release, so a release tag gives the exact pair:

```sh
git -C PipePipe rev-parse v5.4.0:PipePipeClient     # client commit to merge
git -C PipePipe rev-parse v5.4.0:PipePipeExtractor  # extractor commit to check out
```

## Merge rules

1. Follow stable upstream releases only. Pre-releases (`-beta`) are skipped.
2. Merge the pinned client commit into a `sync/vX.Y.Z` branch with a merge
   commit. Never rebase the fork onto upstream.
3. If upstream fixes a problem that a fork patch also fixes, keep the upstream
   fix and drop ours. Keep our change only if it covers a case the upstream fix
   does not, and say so in the pull request.
4. When both sides add code in the same place (listeners, preference setup),
   keep both.
5. When upstream moves or rewrites code a patch depends on, port the patch to
   the new code instead of restoring the old code. If the port is not obvious,
   stop and ask.
6. Reset `qpipepipeRelease` in `app/qpipepipe.gradle` to 1 (see Versions).
7. After the merge, `./gradlew assembleDebug` must pass, then run the release
   checks below. The app has no unit tests (`testDebugUnitTest` reports
   `NO-SOURCE`), so compiling is the only check that runs without a phone.

## Identity and versions

`app/qpipepipe.gradle`, applied last from `app/build.gradle`, overrides
upstream's app id, app name, version and APK file names. Upstream keeps
editing `baseVersionCode` and `appVersionName` in `build.gradle`; leave them
as upstream sets them.

- App id `io.github.rumisketches.qpipepipe` (`.debug` for debug builds). It
  must never change, or installed apps stop receiving updates.
- Version name `<upstream version>-q<N>`, e.g. `5.4.0-q1`. Bump
  `qpipepipeRelease` for every fork release; reset it to 1 when merging a new
  upstream release.
- Version code `upstream code * 1000 + N * 10 + ABI` (ABI 1-4).
- GitHub release names must be `v<version name>` (e.g. `v5.4.0-q1`):
  `NewVersionWorker` reads them from the QPipePipe releases to offer updates.
- Release notes: rewrite `qpipepipe_update_log` (`strings_qpipepipe.xml`) for
  every fork release. The What's New dialog shows it under "QPipePipe
  <version>:" and, when the upstream release changed, upstream's
  `update_log` under "PipePipe <version>:". Never edit `update_log`: it is
  upstream's text and translations.
- Launcher icon: `qpipepipe_launcher_*` drawables and
  `qpipepipe_launcher_background`, wired in `mipmap-anydpi-v26/ic_launcher.xml`;
  the legacy `mipmap-*/ic_launcher.png` are replaced.

## Forking QPipePipe

To publish your own build, change these, or your users get QPipePipe's updates and
announcements and your crash reports reach QPipePipe:

1. `app/qpipepipe.gradle`: `qpipepipeApplicationId`, `qpipepipeGithubRepo` and
   `qpipepipeContactEmail`. The update check, the announcements and the error screen
   read them from `BuildConfig`.
2. `github_url` in `res/values/donottranslate.xml` (the GitHub link in About). It stays
   in upstream's file to avoid a conflict on every merge.
3. Your own keystore, and the certificate fingerprint in `README.md`.
4. `ERROR_EMAIL_SUBJECT` in `ErrorActivity` and the app name in `app/qpipepipe.gradle`
   if you rename the app.

`tools/release/build-release.ps1` and `tools/qcheck/results/` are the QPipePipe
maintainer's: the script has their keystore path, the results come from their phone.
`tools/qcheck/qcheck.py` works with any phone (`--package <your debug app id>`).

## Signed release build

The maintainer builds the signed APKs locally; the keystore and its password never
leave their machine:

```powershell
powershell -ExecutionPolicy Bypass -File tools\release\build-release.ps1
```

The script asks for the keystore password, builds `assembleRelease` and lists the
APKs in `app\build\outputs\apk\release\`.

## Release checks

With an unlocked phone connected over adb and the new debug build at hand:

```sh
python tools/qcheck/qcheck.py --apk app/build/outputs/apk/debug/QPipePipe_<version>-arm64-v8a-debug.apk \
    --baseline tools/qcheck/results/<previous release>.json
```

The script upgrades the installed debug build, checks that QPipePipe settings
survive, runs the feature checks marked "Check" below with each feature on and
off (a check that also passes with the feature off is reported as
INSENSITIVE), measures app and video start times, compares them with the
baseline (15% threshold) and collects crashes. It restores the user's
preferences at the end. Keep the result file of each release in
`tools/qcheck/results/`.

The "no crash on reconnect" patch has no automated check: the OS has to kill
and recreate the player service, which adb cannot trigger reliably.

## QPipePipe settings layer

Fork code and resources live in their own files so upstream merges rarely touch
them:

- `org.schabi.newpipe.qpipepipe`: `QPipePipeSettingsFragment` (the
  "QPipePipe" screen at the top of Settings) and `QPipePipePreferences`.
- `res/xml/qpipepipe_settings.xml`, `res/values/settings_keys_qpipepipe.xml`,
  `res/values/strings_qpipepipe.xml`, `res/values/colors_qpipepipe.xml`.

Upstream files touched only to plug the layer in: one entry in
`main_settings.xml`, one line in `SettingsResourceRegistry`. New fork settings
go in this screen, not in the upstream screens. Preference keys predate the
screen and must not be renamed, or users lose their choices.

## Fork patches

### Tapping a video always plays it

Commit `292f3839d`, made optional in the QPipePipe screen. Files:
`NavigationHelper.openVideoDetailFragment`, `VideoDetailFragment.isAutoplayEnabled`.
Key: `tap_always_plays_key` (default on).

When on, tapping a video starts it even when Autoplay is set to "Wi-Fi only" or
"Never"; the Autoplay setting still governs automatic starts such as
auto-queue. When off, upstream behaviour applies. Both places must check
`isTapAlwaysPlays() || isAutoplayAllowedByUser()`.

Check: Autoplay "Never", tap a video from the feed: it plays with the switch on
and does not start with the switch off.

### Swipe actions on stream rows

Commits `de610c1d3`, `b4ae6219b`, `e3ef93aad`. Code in
`info_list/swipe/` (`SwipeAction`, `SwipeActionPreferences`,
`SwipeActionPainter`, `StreamSwipeItemTouchCallback`), wired in
`BaseListFragment`, `InfoListAdapter`, `BaseLocalListFragment`,
`LocalItemListAdapter`, `LocalPlaylistFragment`, `BookmarkFragment`,
`FeedFragment`, `ThemeHelper.getItemViewMode`, `AppearanceSettingsFragment`,
`QPipePipeSettingsFragment.initSwipeActionsPreference`. Keys:
`enable_swipe_actions_key`, `swipe_left_action_key`, `swipe_right_action_key`.

Settings > QPipePipe sets one action per swipe direction (enqueue, enqueue
next, background, popup, add to playlist, share, download, open in browser,
mark as watched, remove from playlist). Enabling it forces list view. Rows snap
back after the action. Enqueue with no active player opens the video detail
instead of starting background audio.

Check: enable swipe actions, swipe left and right on rows in home, search,
history, a local playlist and a channel group feed; the rows snap back and the
action runs.

### Clear-queue confirmation with enqueue options

Commit `fb4865a17`. Files: `VideoDetailFragment` (`loadVideoIfUserConfirms`
and the dialog), `QPipePipePreferences.isClearingQueueConfirmationRequired`.
Key: `clear_queue_confirmation_key`.

Upstream removed this setting. The fork restores it: when a queue is playing
and the user opens a different video, a dialog offers Play now, Enqueue next
and Enqueue.

Check: enable the setting, play a playlist, tap another video from search: the
dialog appears and each choice behaves as labelled.

### No crash when the service reconnects without a player

Commit `55fec9953`. File: `VideoDetailFragment.onServiceConnected`.

The player service is bound with `BIND_AUTO_CREATE`, so after the OS kills and
recreates it the fragment can connect before a `Player` exists. The guard
returns early instead of dereferencing a null player. Upstream `a43bcc346`
(null intent in `onStartCommand`) fixes a different path; both stay.

### Bug reports go to QPipePipe

Files: `ErrorActivity` (`ERROR_EMAIL_ADDRESS`, `ERROR_EMAIL_SUBJECT`,
`ERROR_GITHUB_ISSUE_URL`, built from `BuildConfig.QPIPEPIPE_CONTACT_EMAIL` and
`QPIPEPIPE_GITHUB_REPO`), `donottranslate.xml` (`github_url`).

The error screen's email and GitHub buttons and the GitHub link in About
point to this fork, so upstream does not receive reports caused by fork code.
Email subjects start with `[QPipePipe]`. A merge must not restore the
upstream addresses.

Check: on the error screen (shown after a crash or a failed video) the email
button addresses the QPipePipe email and the GitHub button opens the
QPipePipe issues.

### Startup notices say where they come from

Files: `QPipePipeNotices`, `MainActivity` (What's New, donation and
announcement dialogs).

Upstream's announcement and donation dialogs are titled "PipePipe: ...".
QPipePipe announcements come from the `Announcement` page of the QPipePipe
wiki, in upstream's format (`## yyMMdd` headings, newest first), titled
"QPipePipe: Announcement". The app reads the page's raw markdown at most every
6 hours; fetching the HTML page on every start slowed video starts by about
half a second. What's New appears after every version change, fork releases
included (upstream only showed it when the version code grew by more than 90).

Check: install over an older build: What's New shows the QPipePipe notes, and
the PipePipe notes too when the upstream version changed.

## Upstream services

The extractor falls back to `api.pipepipe.dev` to decode YouTube signatures
when the local WebView decoder fails. Upstream agreed (October 2026) that
QPipePipe users may use this fallback, so it stays unchanged. Bug reports and
support stay in this repository.

## Dropped patches

- `7729a5383` (null context guard in the decoder error dialog): replaced by
  upstream `fb8e52e9e`.

## Known conflicts

- `VideoDetailFragment` imports: `RECOVERY_UNSET` moved to `PlayQueue` in
  v5.4.0.
- v5.4.0 replaced `PlayQueueItem` with `PlayerMediaItem` (`PlayQueue.getItem()`).
  `loadVideoIfUserConfirms` had to be ported; the merge was clean but did not
  compile.

`git rerere` is enabled locally and remembers these resolutions.
