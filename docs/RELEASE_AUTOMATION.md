# Debug APK distribution workflow

The **Build debug APK and publish prerelease** workflow is manual-only. In GitHub,
open **Actions**, select that workflow, choose **Run workflow**, optionally provide a
release title suffix, and run it from the desired commit on `main`.

The workflow intentionally has no `push` trigger: each execution creates a public,
uniquely tagged prerelease, so automatic publication from every push would create
unreviewed release artifacts.

## What the workflow verifies and publishes

1. Checks out Git LFS content and verifies the bundled ONNX model has exactly
   114,017,796 bytes and SHA-256
   `af4f2e2174960af06a7a7d07810ea7eb2d78fba827be7690abb230159717d250`.
2. Uses JetBrains Java 25, matching `gradle/gradle-daemon-jvm.properties`, then installs
   Android platform 36 and build-tools 36.0.0.
3. Runs JVM tests and builds the debug APK.
4. Checks the Android package identity and the APK's embedded model asset/hash.
5. Generates a QR PNG that points directly to that run's public GitHub Release APK URL.
6. Creates a new GitHub prerelease with the APK, SHA-256 sidecar, and QR PNG as assets.
   The release notes visibly embed the same QR asset and retain the CC BY-NC 4.0 notice.
   Existing releases are never overwritten.

## First-time GitHub configuration

Repository Actions settings must allow the workflow token to have **Contents: read and
write**; the workflow requires that permission solely to create the prerelease and upload
its assets. GitHub Pages, a Pages environment, and a `gh-pages` branch are not used.

The repository is public, and the direct QR target is a public GitHub Release asset.
Do not use this workflow for commercial distribution: the bundled model is CC BY-NC
4.0 and the APK must retain its attribution/provenance notices.

## Validation boundary

Local validation can check YAML/shell syntax, LFS integrity, JVM tests, APK assembly,
and archive contents. Only a manually approved GitHub Actions run can prove GitHub
Release publishing and that GitHub renders the release-notes QR image.
