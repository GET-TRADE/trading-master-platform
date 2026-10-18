# Releases

## Current flow

1. CI (`android-ci.yml`) builds and tests every push and uploads debug APK
   artifacts on each run.
2. Tagging a release (`v0.1.0`, `v0.2.0`, ...) triggers `release.yml`:
   tests run first; if green, release APKs are built, renamed
   (`TradingMaster-`, `TradingMasterAdmin-`, `TradingMasterPro-<tag>.apk`)
   and attached to a GitHub Release with generated notes.

Since v0.1.1 the APKs are **signed** with the Trading Master release key. The
workflow refuses to publish an unsigned APK. The keystore lives only in
repository secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`); the owner holds the only offline copy — keep it safe, it is
required to publish updates.

## Signed production builds (optional)

Never commit a keystore. Instead:

Signing is already wired: the release workflow decodes `KEYSTORE_BASE64` and
passes AGP injected-signing properties (`-Pandroid.injected.signing.*`) to
`assembleRelease`, then verifies each APK carries a v2 signature block before
publishing. To rotate the key someday: generate a new keystore, replace the
four secrets, and note that a new signature requires uninstall/reinstall on
devices.

## Versioning

Bump `versionCode`/`versionName` in each module's `build.gradle.kts` before
tagging. Live-trading arming state intentionally resets on version changes;
users must re-arm after reviewing the new version.
