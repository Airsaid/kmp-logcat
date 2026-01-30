# Releasing

This project uses `CHANGELOG.md` as the single source of truth for release notes.
GitHub Releases are synced from the changelog by CI.

## Prepare a release

1. Bump `VERSION_NAME` in `gradle.properties`.
2. Ensure the dependency snippet in `README.md` and `README.zh.md` uses the `$version` placeholder (no version bump needed there).
3. Move items from `Unreleased` into a new `## [x.y.z]` section in `CHANGELOG.md`.
4. Commit and push the changes.

## Publish

1. Create a GitHub Release with tag `vX.Y.Z` (tag must match `VERSION_NAME`).
2. CI will:
   - verify the changelog + README versions,
   - publish artifacts to Maven Central,
   - sync the GitHub Release notes from `CHANGELOG.md`.

## Manual publish (optional)

If you need to publish without creating a GitHub Release, run the Publish workflow
from Actions and pass the version. Release notes will not be updated in this mode.
