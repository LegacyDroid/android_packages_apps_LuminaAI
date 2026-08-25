# AGENTS.md - LuminaAI

AOSP system_ext overlay assistant ("Lumi-chan") with a Live2D avatar. Part of
the LegacyDroid ROM tree (`packages/apps/LuminaAI`, repo-managed). Not Gradle.

## Build

Built with Soong from the AOSP tree root, never from this directory alone:

```
source build/envsetup.sh && lunch <target>
m LuminaAI            # app
m libluminalive2d     # native Live2D engine only
```

No test/lint/typecheck targets exist here. Verification = successful soong
build plus manual install on device.

## One-time setup (per checkout)

```
./live2d/get_vendor.sh
```

Downloads the Cubism SDK into git-ignored `live2d/vendor/cubism/` and stages
model + shaders into `live2d/assets/`. The IceGirl model cannot be fetched
automatically (Booth requires sign-in): download it manually, drop the .rar
into `live2d/vendor/models/`, re-run the script. The build succeeds without
it; the avatar just reports unavailable at runtime.

## Hard constraints

- NEVER modify anything under `live2d/vendor/cubism/` - vendored SDK sources,
  license-restricted. Our own C++ lives in `live2d/cpp/` and is fair game.
- Never commit `live2d/vendor/` or `live2d/assets/*` (the committed `.keep`
  keeps the dir alive). Licenses forbid redistribution; see
  `live2d/LICENSE-NOTES.md`.
- Every new source file needs the Apache-2.0 SPDX header comment.

## Layout and entrypoints

- Kotlin app: `src/com/legacydroid/luminaai/`
  - `LuminaApp.kt` - Application, self-grants permissions at boot
    (platform-signed priv-app, so this works)
  - `LuminaOverlayActivity.kt` - transparent fullscreen host activity
  - `LuminaRoot.kt` - Compose root; `LuminaSession.kt` is the global state
    singleton wired through everything
  - `data/` - tool registry, notification hub (OTP extraction), memory and
    history stores
  - `live2d/` - Kotlin bridge (`Live2DBridge`), controller, TextureView avatar
- Native engine: `live2d/cpp/` -> `libluminalive2d` (`live2d/Android.bp`)
  - Three modules: prebuilt Cubism Core, compiled Cubism Framework, our JNI
    engine linking both

## Gotchas that will bite

- JNI symbol names in `JniBridgeC.cpp` encode the exact Kotlin class path
  (`Java_com_legacydroid_luminaai_live2d_Live2DBridge_*`). Renaming or moving
  `Live2DBridge` breaks them silently; R8 would also strip the class if not
  for the `-keep` in `live2d/proguard.flags`.
- Native modules use `sdk_version: "24"` (NDK toolchain), not platform, since
  the Cubism Core static libs are NDK-built. Do not "fix" this.
- arm32 is disabled in all modules: the Core SDK ships no armeabi-v7a prebuilt.
- `VendorWarningShield.h` is force-included to beat the tree's global
  `-Werror` on vendored SDK code. Leave the `-include` cflags alone.
- `libluminalive2d` must stay `system_ext_specific` to match the app's
  partition, or artifact_path_requirements fails.
- Engine threading: public `Live2DEngine` methods are mutex-guarded;
  use `PeekInstance()` (never `GetInstance()`) when polling from the UI
  thread, or the engine gets created without a GL surface and never loads.
  All GL calls belong on the dedicated EGL render thread.
- Model expression names are Chinese; English aliases are mapped in
  `Live2DController.EXPRESSION_ALIASES`. New expressions need an alias entry.
