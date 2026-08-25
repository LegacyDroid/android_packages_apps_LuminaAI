# LuminaAI

LuminaAI ("Lumi-chan") is a system overlay assistant for LegacyDroid. It
lives on top of any app as a Material You themed orb and wakes into a full
screen chat with an animated Live2D avatar, system tools and long term
memory.

It is part of the LegacyDroid ROM tree (`packages/apps/LuminaAI`) and builds
with Soong like every other platform app. It is not a Gradle project.

## Features

- Fullscreen transparent overlay over whatever is on screen, dimmed while
  active, dismissed on idle or back press.
- Live2D avatar (Cubism SDK) rendered on its own EGL thread into a
  TextureView. Head and eyes follow touch, taps trigger expressions or
  motions, idle loop keeps her alive.
- Face expressions and motions driven by the assistant through friendly
  English aliases mapped onto the model's names.
- Assistant tools: Wi-Fi state, battery status, notification buffer with
  automatic OTP extraction to clipboard, clipboard access, app search,
  privacy audit and more (see `src/com/legacydroid/luminaai/data/`).
- Persistent memories the user can add, edit and clear, plus resumable chat
  history sessions.
- Markdown rendering in replies, tool approval flow, suggestion chips.
- Power button shockwave effect and ambient particles around the overlay.

## Building

Requires a synced LegacyDroid/AOSP tree. From the tree root:

```
source build/envsetup.sh && lunch <target>
m LuminaAI            # the app
m libluminalive2d     # native Live2D engine only
```

The app installs as a platform-signed privileged `system_ext` priv-app and
grants its runtime permissions itself at boot, so it needs to be shipped in
a ROM image, not sideloaded onto stock Android.

## Avatar setup (one time per checkout)

```
./live2d/get_vendor.sh
```

This downloads the Live2D Cubism SDK for Native into git-ignored
`live2d/vendor/cubism/` and stages the model plus shaders into
`live2d/assets/`.

The IceGirl model cannot be fetched automatically because Booth requires a
sign-in:

1. Download the free model from <https://booth.pm/en/items/5975192>
   (`IceGirl_Live2d.rar`)
2. Put the archive into `live2d/vendor/models/`
3. Re-run `./live2d/get_vendor.sh`

Without the model the build still succeeds; the avatar simply reports as
unavailable at runtime. See `live2d/LICENSE-NOTES.md` for why none of this
content lives in git.

## Repository layout

| Path | Contents |
| --- | --- |
| `src/com/legacydroid/luminaai/` | Kotlin app: Compose UI, session state, assistant tools |
| `live2d/cpp/` | Native render engine and JNI bridge (`libluminalive2d`) |
| `live2d/Android.bp` | Soong modules for the Cubism Core, Framework and engine |
| `live2d/assets/` | Staged model files and shaders (git-ignored, not committed) |
| `res/`, `AndroidManifest.xml` | Overlay activity, notification listener, priv-app permissions |

Agents working in this repo should read `AGENTS.md` first; it lists the
build commands, license constraints and the native/Kotlin coupling traps.

## License

All original code (Kotlin sources, `live2d/cpp/`, build files) is licensed
under Apache-2.0:

```
SPDX-FileCopyrightText: 2026 The LegacyDroid Project
SPDX-License-Identifier: Apache-2.0
```

Third party content has separate terms and is never committed to this
repository:

- Live2D Cubism Core: Live2D Proprietary Software License, redistributed
  only when embedded in the app.
- Live2D Cubism Framework: Live2D Open Software License Agreement (LSOSL).
- Ice Girl model by TianYeLuLu: free download, attribution required -
  credit `Live2D:@TianYeLulu` when publishing content featuring her.

Details in [live2d/LICENSE-NOTES.md](live2d/LICENSE-NOTES.md).
