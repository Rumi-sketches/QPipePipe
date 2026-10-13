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
6. After the merge, `./gradlew assembleDebug` must pass, then run the manual
   checks below on a device. The app has no unit tests (`testDebugUnitTest`
   reports `NO-SOURCE`), so compiling is the only automatic check.

## Fork patches

### Manual play ignores the Autoplay setting

Commit `292f3839d`. Files: `NavigationHelper.openVideoDetailFragment`,
`VideoDetailFragment.isAutoplayEnabled`.

Tapping a video is an explicit play, so it starts even when Autoplay is set to
"Wi-Fi only" or "Never". The Autoplay setting still governs automatic starts
such as auto-queue. `isAutoplayEnabled()` must not re-check
`isAutoplayAllowedByUser()`.

Check: Autoplay "Never", tap a video from the feed: it plays.

### Swipe actions on stream rows

Commits `de610c1d3`, `b4ae6219b`, `e3ef93aad`. Code in
`info_list/swipe/` (`SwipeAction`, `SwipeActionPreferences`,
`SwipeActionPainter`, `StreamSwipeItemTouchCallback`), wired in
`BaseListFragment`, `InfoListAdapter`, `BaseLocalListFragment`,
`LocalItemListAdapter`, `LocalPlaylistFragment`, `BookmarkFragment`,
`FeedFragment`, `ThemeHelper.getItemViewMode`, `AppearanceSettingsFragment`,
`GestureSettingsFragment.initSwipeActionsPreference`. Keys:
`enable_swipe_actions_key`, `swipe_left_action_key`, `swipe_right_action_key`.

Settings > Gestures sets one action per swipe direction (enqueue, enqueue
next, background, popup, add to playlist, share, download, open in browser,
mark as watched, remove from playlist). Enabling it forces list view. Rows snap
back after the action. Enqueue with no active player opens the video detail
instead of starting background audio.

Check: enable swipe actions, swipe left and right on rows in home, search,
history, a local playlist and a channel group feed; the rows snap back and the
action runs.

### Clear-queue confirmation with enqueue options

Commit `fb4865a17`. Files: `VideoDetailFragment` (`loadVideoIfUserConfirms`
and the dialog), `PlayerHelper.isClearingQueueConfirmationRequired`,
`advanced_settings.xml`. Key: `clear_queue_confirmation_key`.

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

## Dropped patches

- `7729a5383` (null context guard in the decoder error dialog): replaced by
  upstream `fb8e52e9e`.

## Known conflicts

- `GestureSettingsFragment.onCreatePreferences`: keep
  `initSwipeActionsPreference()` and the upstream setup method next to it
  (renamed to `setupVerticalSwipeGestureMutualExclusion()` in v5.4.0).
- `VideoDetailFragment` imports: keep `isClearingQueueConfirmationRequired`;
  `RECOVERY_UNSET` moved to `PlayQueue` in v5.4.0.
- v5.4.0 replaced `PlayQueueItem` with `PlayerMediaItem` (`PlayQueue.getItem()`).
  `loadVideoIfUserConfirms` had to be ported; the merge was clean but did not
  compile.

`git rerere` is enabled locally and remembers these resolutions.
