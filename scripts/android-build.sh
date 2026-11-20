#!/usr/bin/env bash
# Run from any directory; keep downloaded toolchains inside the workspace.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
export ANDROID_HOME="$ROOT/.local/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  archive="$(mktemp "$ANDROID_HOME/commandlinetools.XXXXXX.zip")"
  curl --fail --location --retry 3 \
    https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    --output "$archive"
  unzip -q "$archive" -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm "$archive"
fi

if [[ ! -f "$ANDROID_HOME/platforms/android-34/package.xml" ||
      ! -f "$ANDROID_HOME/build-tools/34.0.0/package.xml" ||
      ! -f "$ANDROID_HOME/ndk/27.2.12479018/package.xml" ||
      ! -f "$ANDROID_HOME/cmake/3.22.1/package.xml" ]]; then
  # sdkmanager reads a finite number of answers; don't let yes's SIGPIPE
  # hide sdkmanager's actual exit status under pipefail.
  set +o pipefail
  yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses
  status="${PIPESTATUS[1]}"
  set -o pipefail
  [[ "$status" == 0 ]] || exit "$status"
  sdkmanager --sdk_root="$ANDROID_HOME" \
    "platform-tools" "platforms;android-34" "build-tools;34.0.0" \
    "ndk;27.2.12479018" "cmake;3.22.1"
fi

# Preserve any developer-supplied properties, including optional service keys.
properties="$(mktemp "$ROOT/.local/local.properties.XXXXXX")"
if [[ -f "$ROOT/local.properties" ]]; then
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ ^[[:space:]]*sdk\.dir[[:space:]]*[:=] ]] || printf '%s\n' "$line"
  done < "$ROOT/local.properties" > "$properties"
fi
printf 'sdk.dir=%s\n' "$ANDROID_HOME" >> "$properties"
mv "$properties" "$ROOT/local.properties"
if [[ "$#" == 0 ]]; then
  set -- assembleDebug
fi

needs_release_signing=0
for task in "$@"; do
  case "$task" in
    *assembleRelease*|*bundleRelease*|*packageRelease*|*installRelease*)
      needs_release_signing=1
      ;;
  esac
done

if [[ "$needs_release_signing" == 1 ]]; then
  required_secrets=(
    ANDROID_KEYSTORE_BASE64
    ANDROID_KEYSTORE_PASSWORD
    ANDROID_KEY_ALIAS
    ANDROID_KEY_PASSWORD
  )
  missing=()
  for name in "${required_secrets[@]}"; do
    if [[ -z "${!name:-}" ]]; then
      missing+=("$name")
    fi
  done
  if [[ "${#missing[@]}" -gt 0 ]]; then
    printf 'Release signing requires these Replit Secrets: %s\n' "${missing[*]}" >&2
    exit 2
  fi

  signing_dir="$ROOT/.local/release-signing"
  mkdir -p "$signing_dir"
  chmod 700 "$signing_dir"
  umask 077
  signing_file="$(mktemp "$signing_dir/keystore.XXXXXX.jks")"
  trap 'rm -f "$signing_file"' EXIT
  # Phone selection and clipboard tools can insert whitespace into wrapped base64.
  if ! printf '%s' "$ANDROID_KEYSTORE_BASE64" | tr -d '[:space:]' | base64 --decode > "$signing_file"; then
    rm -f "$signing_file"
    echo "ANDROID_KEYSTORE_BASE64 is not valid base64." >&2
    exit 2
  fi
  if [[ ! -s "$signing_file" ]]; then
    rm -f "$signing_file"
    echo "The decoded Android signing keystore is empty." >&2
    exit 2
  fi
  export ANDROID_KEYSTORE_FILE="$signing_file"
  unset ANDROID_KEYSTORE_BASE64
fi

if [[ "$needs_release_signing" == 1 ]]; then
  # Keep this shell alive so EXIT removes the temporary signing key on success
  # or failure. Don't retain the signing credentials in a reusable Gradle daemon.
  bash ./gradlew --no-daemon "$@"
else
  exec bash ./gradlew "$@"
fi
