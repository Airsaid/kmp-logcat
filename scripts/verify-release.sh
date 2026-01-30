#!/usr/bin/env bash
set -euo pipefail

version="${1:-}"
if [[ -z "$version" ]]; then
  echo "Usage: $0 <version>" >&2
  exit 1
fi

"$(dirname "$0")/verify-docs-sync.sh" "$version"

if command -v rg >/dev/null 2>&1; then
  changelog_match=$(rg -n "^## \\[$version\\]" CHANGELOG.md || true)
else
  changelog_match=$(grep -E -n "^## \\[$version\\]" CHANGELOG.md || true)
fi

if [[ -z "$changelog_match" ]]; then
  echo "CHANGELOG.md is missing a section for version $version." >&2
  exit 1
fi
