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
   fingerprint in `tools/release/certificate-sha256.txt` and `README.md`.
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

Fork strings are translated in `res/values-*/strings_qpipepipe.xml` and
`strings_qpipepipe_selection.xml`, one file each per locale that has an
upstream `strings.xml` with content (87). They are not on Weblate: when a fork
string is added or changed, translate it in every one of those files, keeping
the plural quantities of the upstream file of that locale. `qpipepipe_update_log` and `qpipepipe_settings_title` stay English
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

### Multiple selection of videos

Code in `qpipepipe/selection/` (`MultiSelect`, `SelectionController`,
`SelectionDecoration`, `SelectionActions`), `res/layout/qpipepipe_selection_bar.xml`,
`res/values/strings_qpipepipe_selection.xml` (translated in every
`values-*/strings_qpipepipe_selection.xml`), `styles_qpipepipe.xml`,
`ids_qpipepipe.xml`. Upstream files touched: `InfoItemDialog` (no dialog while
selecting), `BaseListFragment` and `BaseLocalListFragment`
(install, toolbar button, `getMultiSelectRemover`), `FeedFragment`,
`LocalPlaylistFragment` and `StatisticsPlaylistFragment` (remove, no drag or
swipe while selecting), `LocalItemListAdapter.getLocalItem`, `styles.xml`
(`windowActionModeOverlay`, `actionModeBackground`).

A toolbar button, placed after upstream's icons so it never pushes one of them
into the overflow menu (where there is no room, it is in the overflow menu
itself, e.g. local and remote playlists), starts selection mode. The long-press
menu is unchanged. In selection mode taps select videos (headers keep their
taps), an action mode over the toolbar, in the toolbar's color, shows the count and Select all, and a bar
at the bottom applies one action to the selected videos in the order they were
picked: queue, play next, background, popup, add to playlist, download (video
or audio only, through "Download all"'s sequential downloader), and under More
mark as watched, share links, remove from the playlist or the history. Back or
the arrow leave selection mode, and so do leaving the screen or the list going
out of sight (another tab, the player sheet collapsing). Videos are kept by key
(url, or place in a local playlist), not by row, because history and feed
reload while selecting. A long press selects too, and one that unselects the last
video keeps the mode open, so the row's own long press doesn't open the menu. The selection
state is drawn by an item decoration, so no row layout changes. Comments and
the playlists list are excluded in `MultiSelect.holdsVideos`.

Check: compare the toolbars with upstream's on home tabs, playlists, a local
playlist, history, search and a channel: only "Multiple selection" is added. In
search, tap it and select three videos: "3 selected" and three check marks. Queue starts or extends the queue with all
three. Back leaves selection without leaving search. In a local playlist,
select one video, More > Remove from playlist: it is gone after reopening. In a
channel, select in Videos and swipe to another tab: selection mode ends.

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

Files: `NewVersionWorker` (QPipePipe releases, `-qN` versions, notification,
"latest version" toast), `App` (update channel description),
`QPipePipeUpdateActivity`, `QPipePipeInstallReceiver`, `AndroidManifest.xml`
(`REQUEST_INSTALL_PACKAGES`, the activity and the receiver). Their texts are the
`qpipepipe_update_*` strings, so they say QPipePipe and are English only.

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
half a second. What's New appears after every version change, fork releases
included (upstream only showed it when the version code grew by more than 90).

Check: install over an older build: What's New shows the QPipePipe notes, and
the PipePipe notes too when the upstream version changed.

### Playlists continue where you left off

Files: `qpipepipe/PlaylistResume`, `qpipepipe/PlaylistResumeControls`,
`qpipepipe/PlaylistResumeDecoration`, `res/layout/playlist_resume_control.xml`
(included by `playlist_header.xml` and
`local_playlist_header.xml` in place of `playlist_control.xml`),
`StreamHistoryDAO.getLatestPlayedAmong`, `LocalPlaylistFragment`,
`PlaylistFragment`. Key: `playlist_resume_key` (default off, one value for the
whole app, set from the switch in the header).

The playlist header has a main play button, Background and Popup as icon
buttons, and a "Continue where you left off" switch. The resume point is the
playlist video started most recently according to the watch history, or the
next one when that video was watched past 90%. The switch row only shows up
when that point is past the first video. When the switch is on, the three
buttons start from it, the main one says "Resume from #N", and the list marks
that video with a bar and a "Resume here" label. The label is an item
decoration, so list, grid and card rows need no changes. The controls follow
list changes (pages loaded, sorting, dragging) through an adapter observer.

In remote playlists only the pages loaded so far are searched. Shuffled
background play and the long-press enqueue actions still start from the
beginning. Channels and the feed keep upstream's `playlist_control.xml`.

Check: in a local playlist, play the second video to the end and the third for
a few seconds, turn the switch on: the main button says "Resume from #3" and
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

Debug builds append each reported error (time, version and debug number, action,
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

### Numbered debug builds

Files: `app/qpipepipe.gradle`, `qpipepipe/DebugBuildNotes`, `MainActivity`
(one call after the announcements), `tools/qcheck/qcheck.py`.

A debug build installed on the user's phone gets a sequential number and says
what to test with it. `app/src/debug/assets/debug_build.txt`, which git
ignores, holds the number on its first line and the notes below it. With the
file present, the app is named "QPipePipe Debug <n>", the APK
`QPipePipe_<version>-d<n>-<abi>-debug.apk`, and the first start of that
number shows the notes in a dialog. Without the file nothing changes. qcheck
marks the notes as seen, like What's New.

Check: write `5` and a line of notes to the file, build and install the debug
APK: the launcher shows "QPipePipe Debug 5" and the dialog appears once.

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

### A long pause or a network change does not end in a 403

Files: `PlayerErrorHandler` (`ERROR_CODE_IO_BAD_HTTP_STATUS`,
`recoverFromForbidden`), `Player.renewExpiredSource`, `PlayerTransportController.play`,
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

Files: `PlayerErrorHandler` (`recoverFromNetworkError`, `networkRetryDelayMs`,
`reloadIfWaiting`), `PlayerMetadataController.errorsToReport`,
`PlayerHistoryController.hasPlayedPosition`, `Player.reloadPlayQueueManager`,
`PlayerTransportController.play`, `PlayerPlaybackStateController` (`STATE_READY`).

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

Upstream v5.5.0 also reworked the behind-live-window branch of
`PlayerErrorHandler`: expect a small conflict there at the next sync.

Check: play a video and cut the network (on a phone driven over adb Wi-Fi, set
Private DNS to an invalid hostname instead): one toast, then retries 2, 4, 8,
10 s apart in the log; restore the network and it resumes within 10 s, or at
once when play is pressed, at the position it was at. To cover a resume after
the OS killed the app: play, pause, cut the network, `adb shell am stop-app
<app id>`, then `adb shell cmd media_session dispatch play`.

### Recovery from a decoder that fails mid-stream

Files: `PlayerErrorHandler` (`recoverFromDecoderFailure`, `OneRetryPerItem`).

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
