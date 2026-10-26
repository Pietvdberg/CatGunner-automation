#!/usr/bin/env bash
set -euo pipefail

mkdir -p /workspace/tooling/java-home
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Duser.home=/workspace/tooling/java-home"
if [[ -f /etc/ssl/certs/java/cacerts ]]; then
    # Reuse the managed machine's trusted roots, including the session proxy CA.
    export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi

if [[ -x /workspace/tooling/jdk/bin/javac ]]; then
    export JAVA_HOME=/workspace/tooling/jdk
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# Task-specific paths keep Android/Gradle caches out of the read-only home directory.
export ANDROID_HOME="${ANDROID_HOME:-/workspace/tooling/android-sdk}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-/workspace/tooling/android-user}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/workspace/tooling/gradle-home}"
mkdir -p "$ANDROID_USER_HOME" "$GRADLE_USER_HOME"
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
    export GRADLE_OPTS="${GRADLE_OPTS:-} -Dhttps.proxyHost=$proxy_host -Dhttps.proxyPort=$proxy_port -Dhttp.proxyHost=$proxy_host -Dhttp.proxyPort=$proxy_port"
fi
cd "$(dirname "$0")/.."
exec ./gradlew "$@"
