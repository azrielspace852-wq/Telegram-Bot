#!/bin/sh
set -eu

APP_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION="${GRADLE_VERSION:-8.11.1}"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
DIST_DIR="$GRADLE_USER_HOME/offlineai-gradle/$GRADLE_VERSION"
GRADLE_BIN="$DIST_DIR/gradle-$GRADLE_VERSION/bin/gradle"
DIST_ZIP="$GRADLE_USER_HOME/offlineai-gradle/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -x "$GRADLE_BIN" ]; then
    mkdir -p "$GRADLE_USER_HOME/offlineai-gradle"
    tmp="${DIST_ZIP}.tmp"
    rm -f "$tmp"

    if command -v curl >/dev/null 2>&1; then
        curl -fsSL --retry 3 --connect-timeout 15 \
          "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" \
          -o "$tmp"
    elif command -v wget >/dev/null 2>&1; then
        wget -q --tries=3 --timeout=15 \
          "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" \
          -O "$tmp"
    else
        echo "curl atau wget diperlukan untuk mengunduh Gradle." >&2
        exit 1
    fi

    rm -rf "$DIST_DIR"
    mkdir -p "$DIST_DIR"
    unzip -q "$tmp" -d "$DIST_DIR"
    rm -f "$tmp"
fi

exec "$GRADLE_BIN" -p "$APP_DIR" "$@"
