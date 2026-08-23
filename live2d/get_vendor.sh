#!/usr/bin/env bash
# ==========================================================================
# LuminaAI - live2d/get_vendor.sh
#
# One-time setup that fetches everything that must NOT be committed to git
# (live2d/vendor/ is git-ignored):
#
#   1. Downloads & extracts the Live2D Cubism SDK for Native into
#      live2d/vendor/cubism/ (the Core binaries may only be redistributed
#      embedded in an app, not in a public source repo).
#   2. Copies the Cubism Framework ES2 shaders (plaintext, not secret) into
#      live2d/assets/ - these ARE part of the app assets.
#
#   The IceGirl model is a free Booth download that requires signing in,
#   so it cannot be fetched automatically:
#     1. Download https://booth.pm/en/items/5975192 (IceGirl_Live2d.rar)
#     2. Place it in live2d/vendor/models/
#     3. Re-run this script; it extracts the plaintext model into
#        live2d/assets/ (the APK asset root).
#
# Usage:  ./get_vendor.sh          (run once per checkout)
# ==========================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENDOR_DIR="$ROOT_DIR/vendor"
CUBISM_DIR="$VENDOR_DIR/cubism"
MODEL_DIR="$VENDOR_DIR/models"
ASSETS_DIR="$ROOT_DIR/assets"

SDK_VERSION="5-r.5"
SDK_DIR="$CUBISM_DIR/CubismSdkForNative-$SDK_VERSION"
SDK_URL="https://cubism.live2d.com/sdk-native/bin/CubismSdkForNative-$SDK_VERSION.zip?event=cubism_sdk_download&sdk_type=Native&user_status=new&user_type=%E5%80%8B%E4%BA%BA&version=$SDK_VERSION&lang=en"
BOOTH_ITEM_URL="https://booth.pm/en/items/5975192"
MODEL_NAME="IceGirl"

require_cmd() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "ERROR: required tool '$1' not found (install: $2)" >&2
        exit 1
    fi
}
require_cmd curl "curl"
require_cmd unzip "unzip"

mkdir -p "$CUBISM_DIR" "$MODEL_DIR" "$ASSETS_DIR"

# --- 1. Cubism SDK ------------------------------------------------------------
if [[ -d "$SDK_DIR/Framework" && -d "$SDK_DIR/Core/lib/android" ]]; then
    echo ">> Cubism SDK $SDK_VERSION already present (skipping download)"
else
    echo ">> Downloading Cubism SDK for Native $SDK_VERSION ..."
    ZIP_FILE="$VENDOR_DIR/CubismSdkForNative-$SDK_VERSION.zip"
    curl -fL --retry 3 -o "$ZIP_FILE" "$SDK_URL"
    echo ">> Extracting SDK ..."
    unzip -o -q "$ZIP_FILE" -d "$CUBISM_DIR"
    rm -f "$ZIP_FILE"
    # Normalize the extracted folder name when it differs.
    if [[ ! -d "$SDK_DIR" ]]; then
        FOUND_DIR="$(find "$CUBISM_DIR" -maxdepth 1 -type d -name 'CubismSdkForNative-*' | head -n1)"
        [[ -n "$FOUND_DIR" ]] && mv "$FOUND_DIR" "$SDK_DIR"
    fi
fi

# --- 2. IceGirl model -----------------------------------------------------------
MODEL_PLAIN_DIR="$MODEL_DIR/$MODEL_NAME"
if [[ -d "$MODEL_PLAIN_DIR" ]]; then
    echo ">> Model '$MODEL_NAME' already extracted (skipping archive)"
else
    RAR_FILE="$(find "$MODEL_DIR" -maxdepth 1 -type f -iname "*$MODEL_NAME*.rar" | head -n1 || true)"
    if [[ -z "$RAR_FILE" ]]; then
        cat >&2 <<EOF

==========================================================================
 ERROR: $MODEL_NAME model archive not found.

 This model is a FREE download by TianYeLuLu, but Booth requires a
 (free) sign-in before downloading, so it cannot be fetched here:

    $BOOTH_ITEM_URL

 Steps:
    1. Download "IceGirl_Live2d.rar" from the page above
    2. Place it in:  $MODEL_DIR/
    3. Re-run this script

 Until then the build works, but Lumi-chan will not render and the
 Live2D tools will report the avatar as unavailable.
==========================================================================
EOF
        exit 1
    fi
    echo ">> Extracting model from $RAR_FILE ..."
    TMP_EXTRACT="$MODEL_DIR/.extract_tmp"
    rm -rf "$TMP_EXTRACT"
    mkdir -p "$TMP_EXTRACT"
    if command -v unrar >/dev/null 2>&1; then
        unrar x -o+ "$RAR_FILE" "$TMP_EXTRACT/" >/dev/null
    elif command -v bsdtar >/dev/null 2>&1; then
        bsdtar -xf "$RAR_FILE" -C "$TMP_EXTRACT"
    else
        echo "ERROR: need 'unrar' (or 'bsdtar') to extract the model archive" >&2
        exit 1
    fi
    MODEL3_FILE="$(find "$TMP_EXTRACT" -name "$MODEL_NAME.model3.json" | head -n1 || true)"
    if [[ -z "$MODEL3_FILE" ]]; then
        echo "ERROR: '$MODEL_NAME.model3.json' not found inside the archive" >&2
        exit 1
    fi
    rm -rf "$MODEL_PLAIN_DIR"
    mv "$(dirname "$MODEL3_FILE")" "$MODEL_PLAIN_DIR"
    rm -rf "$TMP_EXTRACT"
fi

if [[ ! -f "$MODEL_PLAIN_DIR/$MODEL_NAME.model3.json" ]]; then
    echo "ERROR: expected $MODEL_PLAIN_DIR/$MODEL_NAME.model3.json" >&2
    exit 1
fi

# --- 3. Stage APK assets (plaintext) --------------------------------------------
echo ">> Staging model assets into live2d/assets/ ..."
rm -rf "$ASSETS_DIR/IceGirl"*
cp -r "$MODEL_PLAIN_DIR/." "$ASSETS_DIR/"
# Keep the author readme out of the APK (it ships in the repo notes instead).
rm -f "$ASSETS_DIR"/*.txt

SHADER_SRC="$SDK_DIR/Framework/src/Rendering/OpenGL/Shaders/StandardES"
if [[ -d "$SHADER_SRC" ]]; then
    cp -f "$SHADER_SRC"/*.vert "$SHADER_SRC"/*.frag "$ASSETS_DIR/"
else
    echo "WARNING: Framework ES2 shaders not found at $SHADER_SRC" >&2
fi

echo ">> Done. Vendor content staged under live2d/vendor/ (git-ignored)."
