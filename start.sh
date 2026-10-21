#!/bin/sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$PROJECT_DIR"

for tool in node npm; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    printf 'Missing %s. Install Node.js 22.12+ (with npm), then try again.\n' "$tool" >&2
    exit 1
  fi
done

node -e '
  const [major, minor] = process.versions.node.split(".").map(Number);
  if (major < 22 || (major === 22 && minor < 12)) {
    console.error("Node.js 22.12+ is required. Current version: " + process.version);
    process.exit(1);
  }
'

JAVA_CMD=java
if [ -n "${JAVA_HOME:-}" ]; then
  JAVA_CMD="$JAVA_HOME/bin/java"
fi
if ! java_version=$("$JAVA_CMD" -version 2>&1); then
  printf 'Java is unavailable. Install JDK 21 and set JAVA_HOME to its installation directory.\n' >&2
  exit 1
fi
java_major=$(printf '%s\n' "$java_version" | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')
case "$java_major" in
  ''|*[!0-9]*)
    printf 'Could not determine the Java version. Set JAVA_HOME to a JDK 21 installation.\n' >&2
    exit 1
    ;;
esac
if [ "$java_major" -lt 21 ]; then
  printf 'Java 21 or newer is required to run the app. Set JAVA_HOME to a JDK 21 installation.\n' >&2
  exit 1
fi

printf '\nInstalling project dependencies and media tools...\n'
npm ci

printf '\nBuilding the backend and bundled frontend...\n'
cd "$PROJECT_DIR/backend"
./gradlew --console=plain bootJar

SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-demo}
export SPRING_PROFILES_ACTIVE
printf '\nStarting Frame at http://127.0.0.1:%s\n' "${PORT:-8080}"
printf 'Wait for Spring Boot to finish starting, then open that URL. Press Ctrl+C to stop.\n\n'
exec "$JAVA_CMD" -jar build/libs/youtube-local-1.0.0.jar "$@"
