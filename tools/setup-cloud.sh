#!/usr/bin/env bash
set -euo pipefail

mkdir -p /workspace/tooling/java-home
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Duser.home=/workspace/tooling/java-home"
if [[ -f /etc/ssl/certs/java/cacerts ]]; then
    # Reuse the managed machine's trusted roots, including the session proxy CA.
    export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi

if [[ ! -x /workspace/tooling/jdk/bin/javac ]]; then
    mkdir -p /workspace/tooling/downloads /workspace/tooling/jdk
    curl --fail --location --retry 2 \
        https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.21_9.tar.gz.sha256.txt \
        --output /workspace/tooling/downloads/jdk17.sha256
    curl --fail --location --retry 2 \
        https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.21_9.tar.gz \
        --output /workspace/tooling/downloads/jdk17.tar.gz
    python3 - <<'PYJAVA'
import hashlib
import pathlib
import tarfile
archive = pathlib.Path('/workspace/tooling/downloads/jdk17.tar.gz')
expected = archive.with_name('jdk17.sha256').read_text().split()[0]
if hashlib.sha256(archive.read_bytes()).hexdigest() != expected:
    raise SystemExit('JDK checksum mismatch')
with tarfile.open(archive) as source:
    for member in source.getmembers():
        relative = pathlib.PurePosixPath(member.name)
        member.name = str(pathlib.PurePosixPath(*relative.parts[1:]))
        if member.name != '.':
            source.extract(member, '/workspace/tooling/jdk', filter='data')
PYJAVA
fi
export JAVA_HOME=/workspace/tooling/jdk
export PATH="$JAVA_HOME/bin:$PATH"

export ANDROID_HOME="${ANDROID_HOME:-/workspace/tooling/android-sdk}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-/workspace/tooling/android-user}"
mkdir -p "$ANDROID_HOME" "$ANDROID_USER_HOME"
command_line_tools="$ANDROID_HOME/cmdline-tools/19.0"
if [[ ! -x "$command_line_tools/bin/sdkmanager" ]]; then
    archive=$(mktemp /tmp/catgunner-android-tools.XXXXXX.zip)
    trap 'rm -f "$archive"' EXIT
    curl --fail --location --retry 2 \
        https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip \
        --output "$archive"
    printf '%s  %s\n' 7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee "$archive" | sha256sum --check
    python3 - "$archive" "$command_line_tools" <<'PY'
import pathlib
import sys
import zipfile
root = pathlib.Path(sys.argv[2])
with zipfile.ZipFile(sys.argv[1]) as archive:
    for item in archive.infolist():
        relative = pathlib.PurePosixPath(item.filename).relative_to('cmdline-tools')
        if '..' in relative.parts:
            raise ValueError('Unsafe archive path')
        output = root / relative
        if item.is_dir():
            output.mkdir(parents=True, exist_ok=True)
        else:
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(archive.read(item))
            output.chmod((item.external_attr >> 16) or 0o644)
PY
fi
sdk_args=("--sdk_root=$ANDROID_HOME")
if [[ -n "${HTTPS_PROXY:-}" ]]; then
    read -r proxy_host proxy_port < <(python3 - <<'PY'
import os
from urllib.parse import urlparse
proxy = urlparse(os.environ['HTTPS_PROXY'])
if not proxy.hostname or not proxy.port:
    raise SystemExit('HTTPS_PROXY must contain a host and port')
print(proxy.hostname, proxy.port)
PY
    )
    sdk_args+=(--proxy=http "--proxy_host=$proxy_host" "--proxy_port=$proxy_port")
fi
"$command_line_tools/bin/sdkmanager" "${sdk_args[@]}" --licenses < <(yes 2>/dev/null)
"$command_line_tools/bin/sdkmanager" "${sdk_args[@]}" \
    'platforms;android-36' 'build-tools;35.0.0' 'platform-tools'
"$(dirname "$0")/cloud-gradle.sh" assembleDebug testDebugUnitTest lintDebug ktlintCheck
