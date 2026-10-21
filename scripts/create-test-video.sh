#!/bin/sh
set -eu
exec node "$(dirname "$0")/create-test-video.mjs" "$@"
