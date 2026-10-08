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

## Signed release APK workflow

The **Build signed release APK and publish prerelease** workflow is also manual-only. It has no
push or pull-request trigger. It builds `:app:assembleRelease` only after all required signing
inputs are present, then verifies the APK with `apksigner` and compares its signer certificate
SHA-256 fingerprint with a separately configured trusted value. It publishes a unique prerelease
with exactly three public assets: the signed APK, its SHA-256 sidecar, and a QR PNG pointing
directly to the APK asset.

The workflow retains the same LFS model integrity, Android platform 36, Build Tools 36.0.0,
JetBrains JDK 25, package/version, and embedded-model checks as the debug workflow. It removes
the temporary decoded keystore on both success and failure and never uploads it as an artifact or
release asset.

### Create and protect the signing identity

Generate the release keystore once on a trusted local machine. Choose and retain a private alias
and strong passwords; do not use the Android debug keystore.

```bash
keytool -genkeypair -v \
  -keystore mms-swahili-tts-release.jks \
  -alias mms-swahili-tts \
  -keyalg RSA -keysize 4096 -validity 10000
chmod 600 mms-swahili-tts-release.jks
```

Keep an encrypted offline backup of the exact keystore and separately protected records of the
alias/passwords. Limit access to release custodians. Losing this keystore prevents signing
compatible updates; replacing it creates a different application identity for Android update
purposes.

Obtain the trusted certificate fingerprint from that same keystore and alias:

```bash
keytool -list -v -keystore mms-swahili-tts-release.jks -alias mms-swahili-tts
```

Record the displayed `SHA256` certificate fingerprint through a secure channel. The workflow
accepts it with or without colon separators, but it must identify this persistent release key.

### Configure GitHub Actions secrets

In the repository's **Settings → Secrets and variables → Actions**, create these repository
secrets. Never put their values in Gradle files, workflow YAML, logs, issues, or commits.

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Single-line Base64 encoding of the persistent release `.jks` file. |
| `ANDROID_KEYSTORE_PASSWORD` | Password for that keystore. |
| `ANDROID_KEY_ALIAS` | Alias created in the persistent release keystore. |
| `ANDROID_KEY_PASSWORD` | Password for that alias/key. |
| `ANDROID_RELEASE_CERT_SHA256` | Trusted SHA-256 certificate fingerprint obtained with `keytool`. |

For example, create the first value locally without copying the keystore into the repository:

```bash
base64 < mms-swahili-tts-release.jks | tr -d '\n'
```

Store the resulting value directly in GitHub Secrets, not in a shell history, file, or commit.

### Trigger and verify the first signed release

1. Confirm the repository Actions policy permits the workflow token to use **Contents: read and
   write**. This permission is used only to create the prerelease and upload the three public
   assets.
2. In **Actions**, select **Build signed release APK and publish prerelease**, choose the intended
   commit/branch, optionally supply a title suffix, and select **Run workflow**.
3. Review the successful job: it must report model integrity, `apksigner` verification, and a
   certificate fingerprint match before the publishing step runs.
4. Download the APK and sidecar from the resulting prerelease. On a trusted machine, verify it
   with `sha256sum -c MmsSwahiliTtsEngine-release.apk.sha256`.
5. Install with `adb install -r MmsSwahiliTtsEngine-release.apk`, or transfer it to the device and
   use Android's package installer. Then select the engine in Android Text-to-speech settings and
   perform a Swahili speech test.

An existing debug-signed copy of `com.negi.mmsswahilitts` generally cannot be updated by this
release-signed APK because Android requires the same signing certificate for an in-place update.
Uninstall the debug copy first when appropriate (this removes its app data), then install the
release APK. All future updates must use the same persistent release keystore and must increase
`versionCode`; otherwise Android will reject the update.

The same CC BY-NC 4.0 non-commercial distribution restriction applies to signed releases.
