# Releasing

This project uses Release Please to manage version bumps, changelog entries,
GitHub Releases, and Maven Central publishing.

## Commit messages

Release Please reads Conventional Commits merged into `main`.

- `fix:` triggers a patch release.
- `feat:` triggers a minor release.
- `feat!`, `fix!`, or a `BREAKING CHANGE:` footer triggers a breaking release.
- `docs:`, `test:`, `ci:`, and `chore:` do not trigger a release by default.
- Add a `Release-As: x.y.z` footer to force a specific version when needed.

When using squash merge, make sure the final squash title keeps the Conventional
Commits prefix.

## Release pull request

After releasable changes land on `main`, the Release Please workflow creates or
updates a release pull request. The release pull request updates:

- `CHANGELOG.md`
- `gradle.properties`
- `version.txt`
- `.release-please-manifest.json`

Do not manually edit those files for a normal release. Review and merge the
Release Please pull request when you are ready to publish.

## Publish

Merging the Release Please pull request creates the `vX.Y.Z` tag and GitHub
Release. The same workflow then verifies the README dependency snippets and
publishes `:logcat` artifacts to Maven Central.

## Manual retry

If the workflow fails for infrastructure reasons, rerun the failed Release Please
workflow from GitHub Actions after fixing the underlying issue.
