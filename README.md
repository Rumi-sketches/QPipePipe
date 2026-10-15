<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="QPipePipe icon" width="96">
</p>

# QPipePipe

QPipePipe is a fork of [PipePipe](https://github.com/InfinityLoop1308/PipePipe) for
power users who want a better queue experience. It is PipePipe with a few extra
features for building and protecting play queues. Everything else, including the
services it supports, comes from PipePipe.

QPipePipe is not affiliated with PipePipe or NewPipe. Please report QPipePipe
problems here, not to the PipePipe developers.

## Why this fork exists

I use PipePipe every day and kept a few patches for myself to manage queues the way I
wanted. They are not planned upstream, and other people asked for the same things
(see [PipePipe issue #1726](https://github.com/InfinityLoop1308/PipePipe/issues/1726)),
so I made the fork public.

## What QPipePipe adds

All of these have their own screen, Settings > QPipePipe, so they don't mix with
PipePipe's settings:

- **Swipe actions on video rows.** Swipe a video left or right in the home feed,
  search, history, playlists and channel groups to enqueue it, play it next, open
  it in the background or popup player, add it to a playlist, share, download, open
  in the browser, mark as watched or remove it from a playlist. You pick one action
  per direction. Off by default; turning it on switches those lists to list view.
- **Queue protection.** When a queue is playing and you open another video, a
  dialog asks whether to play it now, enqueue it next or enqueue it at the end,
  instead of replacing the queue. Off by default.
- **Tapping a video always plays it.** Playback starts when you open a video, even
  with Autoplay limited to Wi-Fi or turned off. Autoplay still controls automatic
  starts. On by default.

**And more to come!** 🚀

Startup notices say where they come from: "QPipePipe" for this fork's release notes
and announcements, "PipePipe" for upstream's.

## Download

Get the APK for your device from [Releases](https://github.com/Rumi-sketches/QPipePipe/releases).
Most phones need `arm64-v8a`.

QPipePipe has its own app id, so it installs next to PipePipe instead of replacing
it. To move your subscriptions, playlists and history, use Settings > Backup >
Export database in PipePipe, then Import database in QPipePipe.

The app checks this repository for new versions and tells you when one is out.

Releases are signed with this certificate. You can check it with
`apksigner verify --print-certs <apk>`:

```
SHA-256: 83a7ca6a819ee2d59079753ab5f18ff66c2bbfe6234015f8b17c554aac22cd36
```

## Versions

A version is the PipePipe version it is based on plus a QPipePipe release number:
`5.4.0-q1` is the first QPipePipe release on PipePipe 5.4.0, `5.4.0-q2` the next
one, and `5.5.0-q1` the first on PipePipe 5.5.0.

## How it is maintained

QPipePipe follows PipePipe's stable releases; betas are skipped. Each new release
is merged into the fork mostly with the help of AI, mainly to resolve
conflicts between upstream changes and the fork's features faster. When upstream fixes
something the fork had also fixed, upstream's fix is kept.

The merge rules and the list of fork changes are in [FORK.md](FORK.md).

## Credits

QPipePipe exists thanks to:

- [PipePipe](https://github.com/InfinityLoop1308/PipePipe) by InfinityLoop1308 and
  its contributors, which QPipePipe is built on.
- [NewPipe](https://github.com/TeamNewPipe/NewPipe) by TeamNewPipe, which PipePipe
  was built on.

If you want to support the people who build the app itself, support
[PipePipe](https://github.com/InfinityLoop1308/PipePipe).

## License

GNU General Public License v3.0, like PipePipe and NewPipe. See [LICENSE](LICENSE).
