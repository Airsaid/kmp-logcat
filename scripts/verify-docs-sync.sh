#!/usr/bin/env bash
set -euo pipefail

rg_or_grep() {
  if command -v rg >/dev/null 2>&1; then
    rg "$@"
  else
    grep -E "$@"
  fi
}

version_from_gradle() {
  rg_or_grep -n '^VERSION_NAME=' gradle.properties | head -n1 | cut -d= -f2
}

version="${1:-}"
if [[ -z "$version" ]]; then
  version="$(version_from_gradle)"
fi

gradle_version="$(version_from_gradle)"
if [[ "$gradle_version" != "$version" ]]; then
  echo "VERSION_NAME in gradle.properties is '$gradle_version', expected '$version'." >&2
  exit 1
fi

for file in README.md README.zh.md; do
  pattern="com\\.airsaid:logcat:(\\\$version|${version})"
  if ! rg_or_grep -q "$pattern" "$file"; then
    echo "Expected $file to reference version $version." >&2
    exit 1
  fi
done
