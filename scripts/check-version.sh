#!/usr/bin/env bash
# Checks that every component declares the same version as the VERSION file,
# and, on a release tag, that the tag matches it (tag v0.2.0 <-> VERSION 0.2.0).
set -euo pipefail
cd "$(dirname "$0")/.."

expected="$(tr -d '[:space:]' < VERSION)"
status=0

if ! [[ "$expected" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "VERSION '$expected' is not MAJOR.MINOR.PATCH"
  exit 1
fi

check() {
  local file="$1" found="$2"
  if [[ "$found" == "$expected" ]]; then
    echo "ok    $file  $found"
  else
    echo "WRONG $file  $found (expected $expected)"
    status=1
  fi
}

check Multi-Agent-System/build.gradle \
  "$(sed -n "s/^version *= *'\(.*\)'/\1/p" Multi-Agent-System/build.gradle)"

for pkg in Embedded-Systems-gs-main/package.json \
           MAS_system_node_server_on-main/back/backend/package.json \
           MAS_system_node_server_on-main/package.json; do
  check "$pkg" "$(node -p "require('./$pkg').version")"
done

tag="${CIRCLE_TAG:-}"
if [[ -n "$tag" ]]; then
  check "git tag" "${tag#v}"
fi

if ! grep -q "^## \[$expected\]" CHANGELOG.md; then
  echo "WRONG CHANGELOG.md  has no '## [$expected]' section"
  status=1
else
  echo "ok    CHANGELOG.md  [$expected]"
fi

exit $status
