#!/usr/bin/env bash
set -euo pipefail

version="${1:-}"
if [[ -z "$version" ]]; then
  echo "Usage: $0 <version>" >&2
  exit 1
fi

file="CHANGELOG.md"
if command -v rg >/dev/null 2>&1; then
  start_line=$(rg -n "^## \\[$version\\]" "$file" | head -n1 | cut -d: -f1)
else
  start_line=$(grep -E -n "^## \\[$version\\]" "$file" | head -n1 | cut -d: -f1)
fi
if [[ -z "$start_line" ]]; then
  echo "CHANGELOG.md is missing a section for version $version." >&2
  exit 1
fi

if command -v rg >/dev/null 2>&1; then
  next_heading=$(tail -n +$((start_line + 1)) "$file" | rg -n "^## \\[" | head -n1 | cut -d: -f1 || true)
else
  next_heading=$(tail -n +$((start_line + 1)) "$file" | grep -E -n "^## \\[" | head -n1 | cut -d: -f1 || true)
fi
if [[ -z "$next_heading" ]]; then
  end_line=$(wc -l < "$file")
else
  end_line=$((start_line + next_heading - 1))
fi

# Print the section body without the header line.
start_body=$((start_line + 1))
if [[ $start_body -le $end_line ]]; then
  sed -n "${start_body},${end_line}p" "$file" | sed '/^$/d'
fi
