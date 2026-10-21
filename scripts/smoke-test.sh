#!/bin/sh
set -eu
exec node "$(dirname "$0")/smoke-test.mjs" "$@"
