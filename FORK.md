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

The release QPipePipe is currently based on, and those two commits, are recorded
in `tools/upstream/pins.properties`. Local builds need `../PipePipeExtractor`
checked out at the `extractor` commit; CI checks it out from there.

## Upstream watcher

`.github/workflows/upstream-watcher.yml` runs every day at 06:17 UTC and can be
started by hand from the Actions tab. It calls `tools/upstream/check_upstream.py`,
which compares PipePipe's latest stable release with `appVersionName` in
`app/build.gradle` and, for a newer one, merges the pinned client commit into
`sync/vX.Y.Z`:

- **No conflicts:** it resets `qpipepipeRelease` to 1, updates
  `tools/upstream/pins.properties`, builds with the pinned extractor
  (`assembleDebug testDebugUnitTest`) and opens a pull request to `dev` with the
  build result and the release checklist. A failed build means the merge needs
  porting work (merge rule 5).
- **Conflicts:** it pushes `sync/vX.Y.Z` at `dev` and opens an issue listing the
  conflicting files and the commands to merge and resolve them locally.

The maintainer hears about it through GitHub's notification for the pull request
or the issue; on days without a new release nothing happens. An existing
`sync/vX.Y.Z` on origin means the release is being handled, so the watcher does
nothing. GitHub pauses scheduled workflows of public repositories after 60 days
without commits and emails a warning first; re-enable it from the Actions tab. To test it, run it by hand with `based_on` set to an
older version and `dry_run` on: it merges and builds without pushing or opening
anything. Locally: `python tools/upstream/check_upstream.py --dry-run`.

## Pull request checks

`.github/workflows/pr-checks.yml` runs on every pull request to `dev` or `main`:
it builds the debug app with the extractor in `tools/upstream/pins.properties`,
runs `testDebugUnitTest` and the unit tests of `tools/upstream` and
`tools/release`. It needs no secrets, so it also runs on pull requests from forks.

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
6. Reset `qpipepipeRelease` in `app/qpipepipe.gradle` to 1 (see Versions) and pin
   the release in `tools/upstream/pins.properties`
   (`python tools/upstream/check_upstream.py --write-pins vX.Y.Z`). The watcher
   does both for clean merges.
7. After the merge, `./gradlew assembleDebug testDebugUnitTest` must pass, then
   run the release checks below. Upstream has no unit tests; the fork's are in
   `app/src/test/.../qpipepipe/`. Everything else needs a phone.

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
  Tags starting with `nightly_` are nightlies (see "Nightly builds").
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
3. Your own keystore, the `KEYSTORE_BASE64` and `KEYSTORE_PASSWORD` secrets, the
   key alias (`KEY_ALIAS` in `.github/workflows/release.yml`), and the certificate
   fingerprint in `tools/release/certificate-sha256.txt` and `README.md`. For
   nightlies, the `DEBUG_KEYSTORE_BASE64` secret and
   `tools/release/debug-certificate-sha256.txt`.
4. `ERROR_EMAIL_SUBJECT` in `ErrorActivity` and the app name in `app/qpipepipe.gradle`
   if you rename the app.

`tools/release/build-release.ps1` and `tools/qcheck/results/` are the QPipePipe
maintainer's: the script has their keystore path, the results come from their phone.
`tools/qcheck/qcheck.py` works with any phone (`--package <your debug app id>`).

## Publishing a release

1. On a `chore/release-<version>` branch from `dev`: bump `qpipepipeRelease`
   (or keep 1 right after an upstream sync), rewrite `qpipepipe_update_log`,
   run the release checks below and add their result file. Check the What's New
   dialog on the test phone. Merge into `dev`.
2. Merge a pull request from `dev` to `main` with a merge commit.
3. Tag the merge commit and push the tag:
   `git tag v5.4.0-q2 origin/main && git push origin v5.4.0-q2`.
4. `.github/workflows/release.yml` builds with the extractor in
   `tools/upstream/pins.properties`, signs, checks that every APK has the tag's
   version and the certificate in `tools/release/certificate-sha256.txt`, and
   creates a **draft** GitHub Release named after the tag, with notes from
   `tools/release/release_notes.py` (QPipePipe notes, install steps, certificate,
   checksums).
5. Review the draft on the Releases page (signature and version of the APKs, a
   quick install over the previous release) and publish it. Publishing is what
   makes the app offer the update.
6. Merge `main` back into `dev`.

v5.4.0-q2 was the first release made this way.

The workflow signs with two repository secrets, set once by the maintainer from
their own terminal so the password never passes through anyone else:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\keystores\qpipepipe-release.jks")) | gh secret set KEYSTORE_BASE64 -R Rumi-sketches/QPipePipe
gh secret set KEYSTORE_PASSWORD -R Rumi-sketches/QPipePipe
```

Run by hand with `dry_run` (and `source_ref` to build a branch), the workflow
builds unsigned and creates nothing.

Local fallback, with the same checks:

```powershell
powershell -ExecutionPolicy Bypass -File tools\release\build-release.ps1
```

It asks for the keystore password, builds `assembleRelease` and fails unless every
APK in `app\build\outputs\apk\release\` is signed with the release certificate.

## Release checks

Use a dedicated test phone (no screen lock, Wi-Fi on, plugged in) with the
debug build installed. With more than one phone connected, set
`ANDROID_SERIAL=<serial>` (from `adb devices`). Start times depend on the phone
and the network, so the baseline must come from the same phone; the result
file records the model and the script flags a mismatch. The script changes the
app preferences with `run-as`; on ROMs that block it, it uses
`DebugPreferencesReceiver`, which exists only in debug builds
(`app/src/debug/`) and only accepts broadcasts from adb's shell. With the new debug
build at hand:

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
`tools/qcheck/results/`, named `<stamp>-<version or commit>-debug-<phone>.json`;
the baseline is the newest file from the same phone.

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

Fork strings are translated in `res/values-*/strings_qpipepipe.xml`, one file
per locale that has an upstream `strings.xml` with content (87). They are not
on Weblate: when a fork string is added or changed, translate it in every one
of those files, keeping the plural quantities of the upstream file of that
locale. `qpipepipe_update_log` and `qpipepipe_settings_title` stay English
only.

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

The queue that plays is the one on screen when the user acts (`openVideoPlayer`,
`openNormalBackgroundPlayer`, `openPopupPlayer`, `replaceQueueIfUserConfirms`).
Upstream read `playQueue` again after connecting to the player and after the
dialog, but every connection pushes the player's queue into it
(`onQueueUpdate`): on 21/10/2026 at 00:25 Play now on a new video resumed the
16-video queue that was paused in the background instead.

Play now on a video tapped while a queue plays (`loadVideoIfUserConfirms`)
starts it once loaded, in the player that was playing (background, popup or
main), without asking again (`queueReplacementConfirmed`, `playInActivePlayer`).
Before, it only loaded it on screen when the open player was not the main one,
and pressing play then showed the same dialog a second time.

### No crash when the service reconnects without a player

Commit `55fec9953`. File: `VideoDetailFragment.onServiceConnected`.

The player service is bound with `BIND_AUTO_CREATE`, so after the OS kills and
recreates it the fragment can connect before a `Player` exists. The guard
returns early instead of dereferencing a null player. Upstream `a43bcc346`
(null intent in `onStartCommand`) fixes a different path; both stay.

### Related videos load after Android recreates the screen

File: `RelatedItemsFragment` (`loadResult`, `handleResult`).

Upstream `a4b44e259` moved the related videos into the `StateSaver` files, and
`MainActivity.onDestroy` deletes those files whenever Android destroys the
activity. The fragment then came back with its url but no related videos, and
loading them failed with `NullPointerException: The callable returned a null
value`. It now rebuilds them from the stream info of its url (cached, or
fetched). The list header (the auto-enqueue switch) only appears when the
fragment is created with its data.

Check: turn on Developer options > Don't keep activities, open a video, press
Home, reopen the app: the Next tab lists the related videos. Turn the option
off again.

### The play queue survives the OS killing the app

File: `VideoDetailFragment` (`onSaveInstanceState`, `onRestoreInstanceState`,
`StateSaver.WriteRead`).

Upstream saves only the url of the open video, and the navigation stack is
static, so when the OS kills the app in background the video came back alone,
in a new single-item queue. The fragment now writes its play queue with
`StateSaver` (a file in the cache) and, when it is recreated, reuses it if its
current item is the restored video. It covers any queue: local or remote
playlist, channel, edited queue. Removing the app from recents still starts
from scratch, as upstream.

Check: play a playlist, go to the second video, press Home, close the player
(`am broadcast -a <app id>.player.MainPlayer.CLOSE`), `am kill <app id>`, reopen
the app: the second video plays and Previous/Next move through the playlist.

### A reload never restarts the video

File: `Player` (`setRecovery`, `recoveryPositionOf`, `reloadPlayQueueManager`).

The player rebuilds the current source after a network error, a track or
quality change, or when the video track is switched on or off, and resumes from
the recovery position saved in the play queue. Upstream saves it only in some
callers (track changes and the video toggle do not), and drops it when the
duration is unknown (`C.TIME_UNSET`, e.g. a second error while the source is
still being rebuilt): the video then started over from 0. Now
`reloadPlayQueueManager` saves the position itself for every caller, except for
live streams, which resume at the live edge, and an unknown duration no longer
caps the position.

Check: `PlayerRecoveryPositionTest`. In the debug app the playback log
(see "Debug builds keep every error trace") shows each reload with its
recovery position.

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

### Updates are downloaded and installed in the app

Files: `NewVersionWorker` (QPipePipe releases, notification, "latest version"
toast), `QPipePipeReleases` (which release to offer), `App` (update channel
description),
`QPipePipeUpdateActivity`, `QPipePipeInstallReceiver`, `AndroidManifest.xml`
(`REQUEST_INSTALL_PACKAGES`, the activity and the receiver). Their texts are the
`qpipepipe_update_*` strings, so they say QPipePipe and are English only.

`NewVersionWorker` keeps the request and the notification; the choice of the
release is `QPipePipeReleases.findUpdate`, and upstream's version functions left
in the worker are unused. A release build offers the newest release newer than
its own, by name (`v5.4.0-q2`), never one tagged `nightly_`, and pre-releases
only with "Show pre-releases". A release whose name is not a version is skipped.
Debug builds follow the nightlies (see "Nightly builds").

The update notification ("QPipePipe X is available") opens a dialog: download
and install, release notes, later. The APK for the device's ABI is downloaded
to the cache and installed with `PackageInstaller`. Android asks the user to
allow installs from QPipePipe the first time, then to confirm the update, and
only accepts an APK signed like the installed app. The activity refuses another
app id (debug builds) or an older version and offers the release page instead.

Check: install a release build with a lower `qpipepipeRelease` (signed, e.g. a
temporary `v5.4.0-q0` tag built by `release.yml`, then delete the draft and the
tag), check for updates in Settings > Updates, tap the notification: the app
updates itself to the latest release.

### Startup notices say where they come from

Files: `QPipePipeNotices`, `MainActivity` (What's New, donation and
announcement dialogs).

Upstream's announcement and donation dialogs are titled "PipePipe: ...".
QPipePipe announcements come from the `Announcement` page of the QPipePipe
wiki, in upstream's format (`## yyMMdd` headings, newest first), titled
"QPipePipe: Announcement". The app reads the page's raw markdown at most every
6 hours; fetching the HTML page on every start slowed video starts by about
half a second.

After an update, upstream's dialogs come first, as upstream shows them: when
the upstream version changed, "PipePipe: What's New" (`update_log`), then the
donation dialog when upstream's timing allows it. The QPipePipe notes
(`qpipepipe_update_log`) follow in their own "QPipePipe: What's New" dialog,
after every version change, fork releases included. Announcements are fetched
in the background and appear on top whenever they arrive.

Check: install over an older build with a different upstream version:
"PipePipe: What's New", then the donation dialog if due, then "QPipePipe:
What's New". Over a build of the same upstream version: only "QPipePipe:
What's New".

### Playlists continue where you left off

Files: `qpipepipe/PlaylistResume`, `qpipepipe/PlaylistResumeControls`,
`qpipepipe/PlaylistResumeDecoration`, `res/layout/playlist_resume_control.xml`
(included by `playlist_header.xml` and
`local_playlist_header.xml` in place of `playlist_control.xml`),
`StreamHistoryDAO.getPlayedAmong`, `LocalPlaylistFragment`,
`PlaylistFragment`. Key: `playlist_resume_key` (default off, one value for the
whole app, set from the switch in the header).

The playlist header has a main play button, Background and Popup as icon
buttons, and a "Continue where you left off" switch. The resume point is the
video furthest down the playlist among those played past 5% of their length
according to the watch history, or the next one when that video was watched
past 90%. The share is read from the saved position, which upstream only saves
past 5 s or a quarter of the video (`StreamStateEntity.isValid`), so a short
video also needs that much; videos only opened for a moment are ignored. A
video with an unknown duration counts as soon as it is started. The switch row only shows up
when that point is past the first video. When the switch is on, the three
buttons start from it, the main one says "Resume from #N", and the list marks
that video with a bar and a "Resume here" label. The label is an item
decoration, so list, grid and card rows need no changes. The controls follow
list changes (pages loaded, sorting, dragging) through an adapter observer.

In remote playlists only the pages loaded so far are searched. Shuffled
background play and the long-press enqueue actions still start from the
beginning. Channels and the feed keep upstream's `playlist_control.xml`. The
root of `playlist_resume_control.xml` is upstream's `PlaylistControlLayout`, so
Appearance > "Play buttons on list pages" hides the fork's header controls
too, switch included.

Check: in a local playlist of four videos about 2 minutes long, play the second
to the end, the third for 15 s, the fourth for 2 s, then the first for 15 s,
and turn the switch on: the main button says "Resume from #3" and
Background starts from the third video. Turn it off: it starts from the first.

### Android Auto follows the car connection

Files: `util/CarConnectionMonitor` (replaces upstream's
`CarConnectionStateReceiver`), `App`, `DeviceUtils.isUsingFromAndroidAuto`,
`util/MediaButtonReceiver`, `PlayerService` and `PlayerServiceForAuto`
(`onStartCommand`, `onCreate`), `PlayerServiceInterface.getMediaSessionConnector`,
`MediaSessionManager`, `PlayerThumbnailController`,
`mediabrowser/MediaBrowserImpl` and `MediaBrowserCommon.changedKeys`.

Upstream keeps two player services and picks one from the car connection state.
The fork fixes how that state and the Android Auto session are handled:

- The state comes from `CarConnection.getType()` for the whole process.
  Upstream read it from the `CAR_CONNECTION_UPDATED` intent, which carries no
  state, so every broadcast meant "connected" and leaving the car was never
  noticed. Connecting stops `PlayerService` only if it runs, and unbinds it
  (upstream created it to stop it and kept it bound). Leaving the car pauses
  the car's player, which keeps serving the phone until it is closed
  (`PlayerServiceForAuto.hasActivePlayer`), so there are never two players.
  With "Disable Android Auto" on, playback always uses `PlayerService`.
- `MediaButtonReceiver` keeps the `MEDIA_BUTTON` action, and a media button
  with nothing to play posts a placeholder notification before stopping the
  service. Upstream dropped the action, so the button did nothing and Android
  reported an ANR ("startForegroundService() did not then call
  startForeground()").
- `PlayerServiceForAuto` owns the only `MediaSessionConnector` of its session
  and creates it before the player, which reuses it. Upstream had a second
  connector that overwrote the playback state, and the first player created a
  separate session.
- The session metadata is rebuilt when the thumbnail loads, and the previous
  stream's thumbnail is dropped, so the car no longer shows the old cover.
- The media browser listeners log their errors and notify only the playlists
  that changed; remote playlists are watched through `playlistRemoteDAO`
  (upstream watched the local table with remote ids).

Check: with the Desktop Head Unit (`extras;google;auto`, Android Auto
developer settings: unknown sources, head unit server), connect and play from
the car's History; close the DHU and play in background from the phone: the
log shows `CarConnectionMonitor: Car connected: false`, the car's player is
paused and keeps the new video; close it and play again: `MainPlayer` handles
it; reconnect: only `PlayerServiceForAuto` runs. With the app idle,
`adb shell am start-foreground-service -a android.intent.action.MEDIA_BUTTON -n
<app id>/org.schabi.newpipe.player.PlayerServiceForAuto` gives no ANR.

### Recent queues

Files: `qpipepipe/recentqueues/` (database, recorder, list and detail screens,
Playlists switch), `qpipepipe/PlaylistProgressLines`, `PlaylistProgress`,
`StreamProgress`, `res/layout/qpipepipe_recent_queue*.xml`,
`qpipepipe_bookmarks_tabs.xml`. Upstream files touched: `PlayQueue` (session id
and source playlist), `PlayerStartController` (three calls to
`RecentQueueRecorder`), `LocalPlaylistFragment.getPlayQueue` (source
playlist), `BookmarkFragment` and `fragment_bookmarks.xml` (the switch),
`LocalPlaylistItemHolder` (date and bar; the bar is added from code so the
row layouts stay upstream's), `App` (registers `RecentQueueUndo`),
`PlaylistStreamDAO.getPlaylistProgress`, `StreamHistoryDAO.getProgressOf`.
Key: `recent_queues_key` (default off, in the QPipePipe settings).

While the setting is on, the queue of the player is written to a separate
database, `qpipepipe_recent_queues.db`, two seconds after it last changed.
Keeping it apart from `newpipe.db` means upstream's schema versions and
migrations never collide with the fork's; the price is that recent queues are
not in the settings export. Rules:

- A queue started from a local playlist is one entry per playlist ("From:
  name"). Videos the user enqueues on it are appended to that playlist, except
  those already in it, and are marked "added". This holds for single videos and
  for a whole playlist or channel; of a remote playlist only the videos already
  loaded join the queue, so only those are appended. Auto-queued related videos
  are never appended. Appends and saves run on one background thread, so quick
  enqueues cannot duplicate a video or collide on the playlist index.
- When one enqueue appends two or more videos, the screen in front shows "N
  videos added to name" with Undo for 4 seconds. Undo takes them back out of
  the playlist, only if they still follow the videos it had before, and out of
  the queue: all or nothing.
- Any other queue is one entry per session, kept once the user built it: it
  started with two or more videos, or the user enqueued something. Videos opened
  one by one, with related videos auto-queued, leave no entry. A session
  survives player switches and process death, and resuming an entry continues it
  instead of creating a new one.
- Queues of remote playlists and channels are not recorded.
- Nothing is deleted automatically: the user deletes one entry or uses Clear
  all. Deleting the entry of the queue playing now does not bring it back while
  that queue plays. Deleting an entry linked to a playlist leaves the playlist.
- Resuming a linked entry links the queue again only if the playlist with that
  id still has the saved name, since a backup import can renumber playlists.
- "Save as playlist" links the entry to the new playlist, and the queue too if
  it is still playing: later enqueues go to that playlist and the entry no
  longer offers saving, so one queue never makes two playlists.

The Playlists page gets a "Playlists / Recent queues" switch. Rows stay short:
a local playlist row shows when it was last played (or "Never played") and a
bar of the share of its videos watched (past 90%); a queue row shows title,
date and the same bar. The video count is on the thumbnail. Remote playlist rows are unchanged:
their videos are not stored locally. The detail screen shows "2/134 videos ·
85:23" (position, length, total hours and minutes) and "N videos added since
date", resumes the queue in the three players, opens the
linked playlist or saves the queue as a new playlist, and marks each video as
playing, watched (past 90%), partly watched or to watch.

Check: turn the setting on. Open a video, enqueue a second one: "Recent queues"
lists one queue with 2 videos. Play a local playlist and enqueue a video that is
not in it: the playlist grows by one, and its queue entry says "1 video added to
the playlist". Enqueue a video already in it: nothing is added and the count
stays at 1. Play a YouTube playlist: no entry appears. Save a playing queue as a
playlist, then enqueue a video: the entry reads "From: <name>" and the new
playlist grows by one. Enqueue a whole YouTube playlist on a queue from a local
playlist and tap Undo within 4 seconds: the local playlist is back to its size.

### Debug builds keep every error trace

Files: `DebugErrorLog` and `RepeatFilter` (new), `ErrorUtil` (`openActivity`,
`showSnackbar`, `createNotification`), `ErrorPanelHelper.showError`;
`PlayerDebugTrace` (new), called from `Player` (`reloadPlayQueueManager`,
`onPositionDiscontinuity`), `PlayerErrorHandler.onPlayerError`,
`PlayerTransportController.onPlaybackSynchronize` and
`VideoDetailFragment.onQueueUpdate`.

Debug builds append each reported error (time, version with the nightly number, action,
service, request, message, all stack traces) to `Android/data/<app
id>/files/errors/errors-YYYY-MM-DD.log`. Nothing rotates or deletes the files;
only uninstalling the app or clearing its data does. Release builds skip it
(`BuildConfig.DEBUG`). An error shown in several ways (snackbar, then the error
screen) is written once. An entry identical to one written less than a minute
earlier (a failure that loops, e.g. a reload every 3 s while offline) is not
written again but counted, and the count goes out with the next entry (`Repeated
N more times until <time>: <entry>`). The point is to keep traces when the error
notification never appears, for example when the debug app has no notification
permission.

The same file also gets playback events that move the position without an error
on screen (`PlayerDebugTrace`, entries starting with `Playback:`): every player
error, silent or not, with the position it happened at; every reload of the
current video (`Player.reloadPlayQueueManager`); every jump back of 30 s or more
inside a video, except when a video that has ended starts again; the video that
was already playing starting over after a reload because no recovery position
was saved (a video just opened is not reported); the video screen taking the
player's queue in place of a different one it was showing, which is the queue
its play buttons then start. Each entry has the video, queue position, playback
position, recovery position, player state and the calling code path, so a video
that starts over can be traced to its cause. Release builds skip all of it.

Check: in the debug app open a video that does not exist, then `adb pull
/sdcard/Android/data/<app id>/files/errors/` holds an entry with the url and the
trace. Play a video, seek past 1:00 and back to 0:10: the file holds a
`Playback: jump back` entry. Turn off Wi-Fi and data while a video plays: once
the buffer runs out the file gets one reload and one network error per minute,
with the counts of the repeats skipped in between.

### One media session per player

File: `Player.setupMediaSession`.

Starting a queue creates the player's media session twice: once in
`initPlayer()` and again in `PlayerStartController.initPlayback` after the
queue is set. Upstream dropped the first `MediaSessionManager` without
disposing it, so on the phone (`PlayerService`) every queue left one more
active `MediaSessionCompat` with no player behind it, which could take the
media buttons of headphones and car. The fork disposes the previous manager
before creating the next one.

Check: play three different videos in background, then
`adb shell dumpsys media_session`: the app has one `MediaSessionManager`
session, not one per video.

### Nightly builds

Files: `app/qpipepipe.gradle`, `.github/workflows/nightly.yml`,
`tools/release/debug-certificate-sha256.txt`, `qpipepipe/QPipePipeReleases`,
`qpipepipe/QPipePipeUpdateActivity`, `qpipepipe/DebugBuildNotes`,
`qpipepipe/QPipePipeNotices`, `error/DebugErrorLog`, `NewVersionWorker` (hook,
see "Updates are downloaded and installed in the app"), `MainActivity` (one
call after the announcements), `tools/qcheck/qcheck.py`.

A nightly is a debug build (app id `.debug`) with a number. The numbers are one
sequence across versions. The number comes from the Gradle property
`qpipepipeNightly` (`-PqpipepipeNightly=36`, or the environment variable
`ORG_GRADLE_PROJECT_qpipepipeNightly=36`); without it, from the first line of
`app/src/debug/assets/debug_build.txt`, which git ignores. The lines below the
first say what to test: the first start of that number shows them once in a
dialog. The commit is `git rev-parse --short=7 HEAD`.

A nightly of the coming version 5.5.0-q1, number 36, commit `abc1234`:

- version name `5.5.0-q1-nightly36 (abc1234)`, so About, Android's app info, the
  error screen and its report (text, JSON, markdown, email subject) and the
  debug error log all show it;
- app name "QPipePipe Nightly 36";
- APKs `QPipePipe_5.5.0-q1-nightly36-abc1234-<abi>-debug.apk`;
- version code equal to the release of that version: nightlies of one version
  replace each other, and `QPipePipeUpdateActivity` accepts an equal code in a
  debug build;
- What's New keeps the coming version ("QPipePipe 5.5.0-q1"), from
  `BuildConfig.QPIPEPIPE_VERSION`.

A debug build without a number is "QPipePipe Debug", version `5.5.0-q1`, with no
dialog. Release builds do not change.

The update check (Settings > Updates) of a debug build looks only at releases
whose tag starts with `nightly_`. It reads the version from the tag
(`nightly_5.5.0-q1`) and the number and commit from the APK names above, and
offers the nightly with the highest number when its number or its version is
newer than its own. A nightly it cannot read is skipped.

Nightlies are built and published by `.github/workflows/nightly.yml`, run by
hand:

```sh
gh workflow run nightly.yml -R Rumi-sketches/QPipePipe -f ref=dev \
    -f notes="First thing to test\nSecond thing to test"
```

- `ref`: the branch or commit to build, `dev` by default;
- `notes`: what to test, in English like everything on GitHub, one line per
  test, separated by `\n` (real new lines work too);
- `number`: optional; by default the highest number in the APK names of the
  `nightly_` releases, plus one. Required when no nightly is published.

The workflow writes the number and the notes in `debug_build.txt`, builds
`assembleDebug -PqpipepipeNightly=<n>` with the pinned extractor, signs with the
debug key from the `DEBUG_KEYSTORE_BASE64` secret, and checks that every APK has
the number, the commit and the certificate in
`tools/release/debug-certificate-sha256.txt`. Then it replaces the release of
that version, so that the app reads it:

- a GitHub pre-release, never "latest", tagged `nightly_<version>`, e.g.
  `nightly_5.5.0-q1`, on the commit just built: the previous nightly of the
  version and its tag are deleted first;
- the APKs with the names Gradle gives them, and only those of the latest
  build: the app reads the number from the first APK it finds;
- a release name that release builds up to 5.4.0-q2 read as an old version
  (they parse every name when "Show pre-releases" is on, and stop at one they
  cannot read): it starts with `0.0.0-beta`, e.g.
  `0.0.0-beta nightly 5.5.0-q1 #36`;
- notes with the number, the commit, the ref and what to test.

The debug key is the maintainer's `~/.android/debug.keystore`, so nightlies
install over the debug builds already on their phones. It was set once with:

```sh
base64 -w0 ~/.android/debug.keystore | gh secret set DEBUG_KEYSTORE_BASE64 -R Rumi-sketches/QPipePipe
```

A fork sets its own debug key and certificate, or nightlies fail the check.

Check: build with `-PqpipepipeNightly=5` and a line of notes in the file,
install the debug APK: the launcher shows "QPipePipe Nightly 5", the dialog
appears once, About shows `<version>-nightly5 (<commit>)`. The update check:
`QPipePipeReleasesTest`.

### Playback resumes after the OS killed the app

Files: `qpipepipe/LastPlayback`, `PlayerStartController.initPlayback`,
`PlayerPlaybackStateController` (`onCompleted`, `onPlayWhenReadyChanged`),
`Player.destroy`, `PlayerService` and `PlayerServiceForAuto` (`onStartCommand`,
`onGetRoot`, the playback preparer's `onPrepare`), `playback/PlayerMediaSession.play`,
`helper/AudioReactor.onAudioFocusLoss`, `util/CarConnectionMonitor`,
`mediabrowser/MediaBrowserImpl` (`onGetRoot`, `ID_RESUME`),
`MediaBrowserPlaybackPreparer` and `AndroidManifest.xml`.

Upstream keeps the play queue only in memory. Once the OS killed the app in
background, pressing play in the car, on the system media controls or on
headphones did nothing, and Android Auto showed "Could not load your
selection" after a timeout.

The fork saves the queue the player starts to `files/last_playback.ser`, and
saves it again whenever the queue changes, wherever it plays: phone or car, so
the last video started anywhere is the one that comes back. When the last video
of a queue ends, the file is deleted, so a video finished at home does not
come back in the car. Whether the queue was playing or paused is kept too, in
the `last_playback` preferences. With nothing loaded:

- play from Android Auto or the system media controls (`onPrepare` of the
  playback preparer) starts the saved queue in background, from the saved
  position; with no saved queue nothing happens, with no error, and the car
  stays on its lists;
- the play button of headphones or car (`MEDIA_BUTTON`, key down of play,
  play/pause or headset hook) does the same. For Android to deliver it, the
  manifest keeps only `util/MediaButtonReceiver`: upstream also declared
  androidx's `MediaButtonReceiver`, and with two receivers `MediaSessionCompat`
  registers none, so Android had nowhere to send the button once the session
  was gone;
- `onGetRoot` with `EXTRA_RECENT` returns a root whose only child is the saved
  queue's current video, which the system media controls offer to resume, or
  null when there is none.

Opening the app itself still starts from scratch.

When a car connects (the first `onGetRoot` without `EXTRA_RECENT` since the
car last disconnected), Android Auto with its autoplay setting on sends play by
itself twice: right after asking for the root, and again a few seconds later,
once the session has something to play. The car must resume on its own only
what was playing:

- the car's player loads the saved queue as the car connects: playing if it
  was playing, otherwise paused, so the car shows cover and controls and waits
  for the user. With nothing saved it loads nothing;
- `PlayerServiceForAuto.takeCarAutoPlay` recognises the two automatic plays,
  which `PlayerMediaSession.play` ignores while the player is paused and was
  not playing: the first counts only within 2 seconds of connecting, the second
  within 15. Without the first one (autoplay off) none counts, so a play the
  user presses always plays;
- if the phone's player is running, `CarConnectionMonitor` hands its queue,
  position and play state over to the car's player instead of only stopping
  it;
- Android Auto takes the audio focus as it connects, a couple of seconds
  before the app notices the car, and that pauses the phone's player.
  `AudioReactor` records the pause, and for 10 seconds `LastPlayback.wasPlaying`
  still answers true.

Check, with the Desktop Head Unit and the app's media source last used in the
car:

- play a video on the phone and connect: it goes on in the car from the same
  point;
- pause it and connect: the car shows cover and controls, paused, and plays
  only when play is pressed. The same after `adb shell am kill <app id>` with
  the app in background (or `am force-stop` after moving it to the
  background);
- play a local playlist from its second video, pause, kill the app and
  connect: play resumes the same video, and Next goes to the third;
- let a single video end, kill the app and connect: nothing plays and no
  message appears; play does nothing, and opening the app in the car shows its
  lists.

Without the car, `adb shell cmd media_session dispatch play` after the kill
resumes the queue too.

### Playback recovery hooks in PlayerErrorHandler

Files: `qpipepipe/player/` (new: `PlaybackRecovery`, `NoConnectionNotice`,
`OneRetryPerItem`), `PlayerErrorHandler` (hooks only), `Player`
(`playerErrorHandler.getRecovery()`), `MediaSessionManager`.

The three recoveries below (403, network, decoder) live in `PlaybackRecovery`;
the "No internet connection" message in `NoConnectionNotice`.
`PlayerErrorHandler` keeps upstream's code plus these hooks, which must survive
every merge:

- the `recovery` field, `PlaybackRecovery(player, reload, shutdown,
  currentItem)`, right after the class line: it hands over the player actions
  that only the `player` package can reach;
- in `onPlayerError`, the `else if (recovery.handles(error))` branch between the
  SABR check and upstream's `when`. It takes the network error codes,
  `ERROR_CODE_IO_BAD_HTTP_STATUS` and `ERROR_CODE_DECODING_FAILED`, so
  upstream's branches for those codes, and its `recoverFromNetworkError`, no
  longer run. A 403 known only from the cause message of
  `ERROR_CODE_IO_UNSPECIFIED` stays with upstream's IP-blocked dialog;
- `if (!recovery.showsNoConnection(playerError))` before upstream's
  `createErrorNotification(playerError)`;
- `positionMs`, `durationMs` and `PlayerDebugTrace.onPlayerError`, from "Debug
  builds keep every error trace".

The hooks stay off the lines upstream v5.5.0 changes (the behind-live-window
branch, the fields after `lastSurfaceErrorRecoveryMs`, `saveStreamProgressState`):
a trial `git merge-file` of its `PlayerErrorHandler.kt` merges without conflicts.

Check after a merge: `git diff <client commit> -- app/src/main/java/org/schabi/newpipe/player/PlayerErrorHandler.kt`,
with the `client=` commit of `tools/upstream/pins.properties` (upstream's tags
are not in this repo), shows only these lines, and `PlaybackRecoveryRouteTest` passes. If upstream
adds or changes a `when` branch for a code the hook takes, that branch does not
run: decide whether the fork's recovery still applies.

### A long pause or a network change does not end in a 403

Files: `PlaybackRecovery` (`Route.FORBIDDEN`, `recoverFromForbidden`, hooks in
`PlayerErrorHandler` above), `Player.renewExpiredSource`, `PlayerTransportController.play`,
`MediaSourceManager.isCurrentSourceExpired`, `LoadedMediaSource.isExpired`.

YouTube stream URLs expire and are bound to the IP they were extracted from.
Upstream resumed a video paused for hours, or played on after a Wi-Fi/data
switch, on the same URLs and stopped with a 403. Now play() rebuilds an expired
source at the same position first, and a 403 drops the cached stream info and
reloads quietly, without the error notification. A second 403 on the same item
within a minute still stops playback with the IP-blocked message, so refused
fresh URLs don't loop.

Check: pause a video for more than its URL lifetime (hours), or switch between
Wi-Fi and mobile data while it plays: it resumes at the same position.

### No error loop without network

Files: `PlaybackRecovery` (`recoverFromNetworkError`, `networkRetryDelayMs`,
`reloadIfWaiting`, `isNoConnection`, `showsNoConnection`, `release`; hooks in
`PlayerErrorHandler` above), `NoConnectionNotice`,
`PlayerMetadataController.errorsToReport`, `PlayerHistoryController.hasPlayedPosition`,
`Player.reloadPlayQueueManager`, `Player.destroyPlayer`, `PlayerTransportController.play`,
`PlayerPlaybackStateController` (`STATE_READY`), `MediaSessionManager` (error message
provider), `strings_qpipepipe.xml` (`no_internet_connection`), `ic_cloud_off`.

Upstream answered every network error by rebuilding the source at once. With no
connection the rebuild fails as fast as DNS does, so the player looped forever,
each time with a toast, an error notification (two: the player error and the
failed item) and the detail player closing. Now the first failure reloads at
once and notifies; further failures on the same item wait 2, 4, 8, then 10
seconds and stay quiet, until playback gets ready again, which also drops the
waiting reload. Pressing play during a wait reloads at once. Errors of a failed
item that reach ExoPlayer (no network) are reported only by the error handler,
not again by the metadata. An item that failed to load no longer saves its
position: upstream saved 0 at every error, so the video started over once the
network came back and its watch progress was lost.

When the cause is a missing connection (`PlaybackRecovery.isNoConnection`: the
host could not be resolved, reached or answered in time) and the retries above
go on, the first failure
shows "No internet connection" in a notification and a toast, instead of the
bug report notification; the notification goes away when playback gets ready
again, another error takes its place or the player closes. The trace still goes to the debug error log. The
media session (`MediaSessionManager`, error message provider) shows the same
message while the player holds the error, so the car says why it stopped;
other player errors show "Could not play this stream" there instead of no
message at all.

Check: play a video and cut the network (on a phone driven over adb Wi-Fi, set
Private DNS to an invalid hostname instead): one toast, then retries 2, 4, 8,
10 s apart in the log; restore the network and it resumes within 10 s, or at
once when play is pressed, at the position it was at. While it is offline the
phone shows "No internet connection" (toast and notification, no bug report
notification), the notification goes away when playback resumes, and the media
session reports the same message (`adb shell dumpsys media_session`: `state=ERROR`,
`error=No internet connection`), which is what Android Auto shows. To cover a resume after
the OS killed the app: play, pause, cut the network, `adb shell am stop-app
<app id>`, then `adb shell cmd media_session dispatch play`.

### Recovery from a decoder that fails mid-stream

Files: `PlaybackRecovery` (`Route.DECODER`, `recoverFromDecoderFailure`,
`OneRetryPerItem`; hooks in `PlayerErrorHandler` above).

Upstream stopped playback with the bug report notification on
`ERROR_CODE_DECODING_FAILED`. A hardware decoder fails like this when the system
takes it back or its media process restarts (`MediaCodec.CodecException`
`0xffffffe0`, DEAD_OBJECT), which says nothing about the stream. Now the first
such failure of a queue item rebuilds the source at the same position, with a new
decoder and no notification; a second failure of the same item within a minute
stops playback as before. The 403 recovery uses the same once-per-item rule,
kept for each item separately. The MediaCodec workaround hint (async queueing,
`setOutputSurface`) now shows only when the recovery fails, with the error
notification: a decoder that recovers by itself needs no setting changed.

Check: on an emulator with `adb root`, play a video in the video player and kill
the decoder service (`adb shell kill -9 $(adb shell pidof
android.hardware.media.c2@1.0-service-goldfish)`): playback resumes within a few
seconds at the same position; a second kill within a minute stops it with the
error notification.

### More actions on the multiple selection

Files: `qpipepipe/selection/ForkSelectionActions`, `res/menu/menu_selection_qpipepipe.xml`,
`qpipepipe/player/EnqueueNext`.
Hooks in upstream files, which must survive a merge:
`SelectionToolbar` constructor (`ForkSelectionActions.addMenuItems(toolbar)` right
after `inflateMenu`), the first lines of `StreamSelectionActions.handle`
(`ForkSelectionActions.handle(...)`) and the enqueue-next branch of
`PlayerStartController.handleIntent` (`EnqueueNext.moveAppendedAfter(...)` instead
of upstream's single `playQueue.move`, which put only the last of several videos
right after the current one).

Upstream's selection toolbar (v5.5.0, `util/selection/`) gets four more actions
in its overflow menu, on every toolbar of videos (the ones with upstream's
Enqueue: search, channel, playlist, kiosk, feed, history, local playlist; not
bookmarks): Enqueue next, Start playing in a popup, Mark as watched (only with
watch history on; live streams are skipped) and Share (the links, one per
line). Each acts on the selected videos in the order of the list, then the
selection closes like after upstream's actions. The labels are upstream
strings, so there is nothing to translate.

Check: in a playlist, start the selection, pick two videos out of order, and
try each of the four actions: next and popup queue them in list order, the
watched marks appear on both rows, share sends both links. Upstream's actions
still work.

### A queue of videos that can't be loaded does not loop

Files: `qpipepipe/player/UnplayableQueue`, `ValidatedInternet`, `strings_qpipepipe.xml`
(`queue_unplayable`). Hooks in `PlayerMetadataController`, which must survive a
merge: the `unplayableQueue` field after the class line, and
`unplayableQueue.onLanding(tag) &&` in front of the condition that reports the
item errors in `onEvents`.

Upstream gives a video that can't be loaded (extraction failed, video removed)
a `FailedMediaSource`: 2 seconds of silence, then the next item, with a toast
and a bug report notification every time the player lands on it. With "repeat
all" and only such videos, that looped forever. Now each of them is reported
once per queue, and once a whole round of a repeat-all queue found only broken
videos, playback pauses and one toast says "None of the videos in the queue can
be played". Pressing play again goes through one more quiet round, then stops
the same way (a queue of one broken video stays on its silence instead: the
player does not land on it again). Videos that load reset the round, so a queue with at least one
playable video goes on as upstream, its broken videos skipped. An item that
failed for a missing connection is not counted: `PlaybackRecovery` owns it.
That includes a network error the extractor wraps in a `ParsingException`
(player metadata, signature decoding after the page loaded) while Android sees
no internet (`ValidatedInternet`: no default network, or one not validated):
upstream took it for a broken video. With the phone online the same error means
the server of that request is down (the signature decoder is `api.pipepipe.dev`),
and the video counts as broken as upstream, so the queue can't retry forever on
"No internet connection". Hook in `MediaSourceManager`, which must survive a merge:
`UnplayableQueue.isBrokenSource(context, throwable)` as the condition in
`onErrorReturn` of the source loading, in place of upstream's
`ExtractionException || IllegalStateException` (kept inside `isBrokenSource`).
No test goes through the hook: after a merge, check that the line is there.
Without repeat all, only the once-per-queue report changes.

Check: `UnplayableQueueTest` passes. On a phone, play in the background with
repeat all a local playlist of removed videos (the app refuses to add one, so
export the database, add rows to `streams` and `playlist_stream_join` with a
`thumbnail_url`, and import it): one error toast per video, then the queue
message and the player paused, no more toasts. The wrapped network error has
no clean phone check: the app decodes signatures on the phone and calls
`api.pipepipe.dev` only as a fallback, so blocking that host changes nothing.
`UnplayableQueueTest` covers it: offline, the queue doesn't stop; online with
the decoder server down, it stops after one round.

### A video that can't be played says why, and the queue goes on

Files: `qpipepipe/player/UnplayableNotice`, `UnplayableReason`, `UnplayableQueue`
(`Notice`, `playsDespiteLoadError`), `res/layout/player_unplayable_qpipepipe.xml`,
`strings_qpipepipe.xml` (`unplayable_video`, `unplayable_next_in`,
`unplayable_next`). Hooks in `VideoDetailFragment`, which must survive a merge: the
`queueDespiteLoadError` field, its reset at the top of `runWorker`, in the error branch of
`runWorker` the `confirmed` line before `showError` and the block after it
(`UnplayableQueue.playsDespiteLoadError`),
and `queueDespiteLoadError` instead of `null` in the first line that picks the queue in
`openVideoPlayer`.

When the player lands on a video that can't be loaded (removed, private,
age-restricted...), the video player pauses on it and shows the reason in place of the
play buttons, with "Next video in 7 s", Cancel and Next video. When the countdown ends,
or on Next video, the next item plays; Cancel stops the countdown and leaves the
reason and Next video. Next video takes the D-pad focus. The last video of a queue
without repeat, and the popup (too small for the buttons), show the reason (and the
countdown) above the player's own buttons, which stay.
The reason is the extractor's exception, found under the player's wrapping; when the
extractor gives none (a video YouTube removed today only gives "Initial WEB player
response is not valid"), it says "This video can't be played". The notice steps aside
while the queue panel is open. The background player has no screen to show it on and
skips after upstream's 2 seconds of silence. The round of the patch above still ends a
repeat-all queue of broken videos with one message, without a countdown.

Upstream opened a queue (a playlist's Play all or Resume) by loading its current video
in the video page and started the player only once it loaded: when it could not, the
page showed its error and the rest of the queue never played. Now a queue of more than
one video goes to the player anyway; a single video keeps the page's error.

Check: `UnplayableQueueTest` and `UnplayableReasonTest` pass. On a phone, a local
playlist with removed videos (see the patch above): Resume on a removed video opens
the player with the reason; picking a removed video in the queue shows the countdown,
then the next video plays; Cancel keeps it there, Next video moves on at once.

### Downloads wait longer and longer before restarting after a network error

Files: `qpipepipe/download/DownloadRetryBackoff`. Hooks, which must survive a merge: in
`DownloadManager` the `mRetryBackoff` field after `mSelfMissionsControl`, the
`isWaiting` block after the first `continue` in `runMissions`,
`mRetryBackoff.endWaits()` before `runMissions()` in `startAllMissions` and
`mRetryBackoff.forget()` at the top of `pauseAllMissions`; in
`DownloadManagerService` the `mRetryMissions` field after `mHandler`, the two lines
after `handleConnectivityState(false)` in `case MESSAGE_ERROR`, and the two lines after
`handleConnectivityState(false)` in the network callback's `onAvailable`.

A download that fails with a network error (host not resolved or reached, timeout,
SSL) or an HTTP 5xx stays in the queue, and upstream's service started it again as soon
as the error arrived. On a network without a working internet (captive Wi-Fi, a hotspot
without data, DNS failing, a server down) it restarted forever, about 170 times a
minute, keeping radio and CPU busy. Now the queue waits 2, 4, 8 ... seconds, up to 5
minutes, before restarting it; the waits start over once it downloaded something. A new
network (`onAvailable`) or "Start all" ends every wait; Start on the download itself
restarts it at once; "Pause all" cancels the restarts still owed. While a download waits, the service stays in the foreground
(as it did during the loop): starting it again from the background later is not
allowed on recent Android. With no network at all nothing changes: the queue waits for
the network as before. "Download failed" is still shown once.

Check: `DownloadRetryBackoffTest` passes. On a phone, set Private DNS to an invalid
hostname, start a download: `notifyError() code = 1005` in the log after 2, 4, 8 ...
seconds instead of a burst; turning Wi-Fi off and on, Start on the download or "Start
all" retries at once; Start on a waiting download, then "Pause all": no attempt after.

## Upstream services

The extractor falls back to `api.pipepipe.dev` to decode YouTube signatures
when the local WebView decoder fails. Upstream agreed (October 2026) that
QPipePipe users may use this fallback, so it stays unchanged. Bug reports and
support stay in this repository.

## Dropped patches

- `7729a5383` (null context guard in the decoder error dialog): replaced by
  upstream `fb8e52e9e`.
- `1f8455d64` (multiple selection of videos, `qpipepipe/selection/`): replaced
  by upstream's multiple selection in v5.5.0 (`util/selection/`, the
  `menu_multi_select` toolbar button). The order of picking and the bottom bar
  are gone. Its four actions upstream lacks are back on top of upstream's
  selection (see "More actions on the multiple selection").

## Known conflicts

- `VideoDetailFragment` imports: `RECOVERY_UNSET` moved to `PlayQueue` in
  v5.4.0.
- v5.4.0 replaced `PlayQueueItem` with `PlayerMediaItem` (`PlayQueue.getItem()`).
  `loadVideoIfUserConfirms` had to be ported; the merge was clean but did not
  compile.
- v5.5.0 added its own multiple selection to the screens where the fork had
  one: the fork's was reverted before the merge (see Dropped patches), which
  left four conflicts in `BaseListFragment`, `PlaylistFragment` and
  `LocalPlaylistFragment` (swipe actions and "Playlists continue where you left
  off" next to upstream's list filter, play-button setting and back handling:
  keep both).
- v5.5.0 made `playlist_control.xml`'s root a `PlaylistControlLayout` and calls
  `configure(page)` on it: `playlist_resume_control.xml` has the same root, so
  that call works on the fork's header too.
- `MediaSessionManager`: v5.5.0's `seekInsteadOfSkip` navigator and media
  button handler go on the connector the fork reuses from
  `PlayerServiceForAuto`, next to the fork's error message provider.
- `VideoDetailFragment` saved state: v5.5.0 dropped `currentSponsorBlockMode`;
  keep only the fork's queue saving (`StateSaver`).
- `MainActivity` imports: keep the fork's (`DebugBuildNotes`, `QPipePipeNotices`)
  and upstream's `SelectionToolbar`.

`git rerere` is enabled locally and remembers these resolutions.
