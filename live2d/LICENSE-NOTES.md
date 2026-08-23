# Live2D licensing notes

LuminaAI embeds a Live2D avatar ("Lumi-chan") rendered with the Cubism SDK.
Three separate licenses apply; none of the covered content is committed to
this repository — it is staged locally by `live2d/get_vendor.sh`.

## 1. Live2D Cubism Core (binary)

* License: Live2D Proprietary Software License
  https://www.live2d.com/eula/live2d-proprietary-software-license-agreement_en.html
* The license permits redistribution of the files listed in
  `Core/RedistributableFiles.txt` **only as part of an application** delivered
  to end users. Committing the raw binaries to a public source repository is
  NOT permitted — hence they live in git-ignored `live2d/vendor/cubism/`.
* Distribution inside the built LuminaAI APK / LegacyDroid ROM images is the
  sanctioned path.

## 2. Live2D Cubism Framework (source)

* License: Live2D Open Software License Agreement (LSOSL)
  https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html
* LSOSL section 2.2.3 permits redistribution to third parties provided the
  recipients succeed to the agreement. The framework sources are therefore
  fetched by script and compiled locally; this repository does not re-host
  them either, keeping all Cubism content in one git-ignored place.
* Relicensing the framework sources under any other license (including this
  project's Apache-2.0) is prohibited by LSOSL section 5.6.

## 3. Ice Girl model

* Author: TianYeLuLu — https://tianyelulu.booth.pm
* Item: 【Free/完全に無料】Ice Girl — https://booth.pm/en/items/5975192 (0 JPY)
* Terms summary: free for commercial and personal use (VTuber events, videos,
  SNS icons/headers). **Attribution required**: credit `Live2D:@TianYeLulu`
  when publishing content featuring the model. Redistribution / resale of the
  model data itself is prohibited — which is why the archive is never
  committed here and must be downloaded manually (Booth requires sign-in).
* Derivative works are allowed.

## Application-side code

Everything under `live2d/cpp/` (the engine, JNI bridge and platform layer,
derived from the Cubism SDK sample structure) plus all Kotlin integration is
LegacyDroid project code under Apache-2.0.
