#!/bin/sh
set -e

APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
DISTRIBUTION_URL="https://services.gradle.org/distributions/gradle-8.13-bin.zip"
DIST_NAME="gradle-8.13"
WRAPPER_DIR="$APP_HOME/gradle/wrapper"
DIST_DIR="$WRAPPER_DIR/$DIST_NAME"
GRADLE_CMD="$DIST_DIR/bin/gradle"

if [ ! -x "$GRADLE_CMD" ]; then
    if ! command -v python3 >/dev/null 2>&1; then
        echo "python3 is required to bootstrap Gradle." >&2
        exit 1
    fi
    tmp_dir=$(mktemp -d 2>/dev/null || mktemp -d -t gradle-wrapper)
    cleanup() { rm -rf "$tmp_dir"; }
    trap cleanup EXIT INT TERM
    archive="$tmp_dir/gradle.zip"
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL "$DISTRIBUTION_URL" -o "$archive" || { echo "Failed to download Gradle distribution." >&2; exit 1; }
    elif command -v wget >/dev/null 2>&1; then
        wget -q "$DISTRIBUTION_URL" -O "$archive" || { echo "Failed to download Gradle distribution." >&2; exit 1; }
    else
        echo "Neither curl nor wget is available to download Gradle." >&2
        exit 1
    fi
    python3 - "$archive" "$WRAPPER_DIR" "$DIST_NAME" <<'PY'
import os, shutil, sys, zipfile
archive, dest, dist_name = sys.argv[1:4]
prefix = dist_name + '/'
with zipfile.ZipFile(archive) as zf:
    members = [info for info in zf.infolist() if info.filename.startswith(prefix)]
    if not members:
        raise SystemExit(f'Missing {prefix}')
    target_root = os.path.join(dest, dist_name)
    if os.path.isdir(target_root):
        shutil.rmtree(target_root)
    for info in members:
        relative = info.filename[len(prefix):]
        if not relative:
            continue
        target = os.path.join(target_root, relative)
        if info.is_dir():
            os.makedirs(target, exist_ok=True)
        else:
            os.makedirs(os.path.dirname(target), exist_ok=True)
            with zf.open(info) as src, open(target, 'wb') as dst:
                dst.write(src.read())
PY
    chmod +x "$GRADLE_CMD"
    trap - EXIT INT TERM
    cleanup
fi

exec "$GRADLE_CMD" "$@"
