# Releases

## Current flow

1. CI (`android-ci.yml`) builds and tests every push and uploads debug APK
   artifacts on each run.
2. Tagging a release (`v0.1.0`, `v0.2.0`, ...) triggers `release.yml`:
   tests run first; if green, release APKs are built, renamed
   (`TradingMaster-`, `TradingMasterAdmin-`, `TradingMasterPro-<tag>.apk`)
   and attached to a GitHub Release with generated notes.

These APKs are **unsigned test builds** until a signing key is configured.

## Signed production builds (optional)

Never commit a keystore. Instead:

1. Generate a keystore locally (e.g. Termux + keytool).
2. Add repository secrets:
   - `KEYSTORE_BASE64` — base64 of the `.jks`
   - `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
3. In the release workflow, decode the keystore into a file and add a signing
   config to each app module, e.g. in `android { signingConfigs { release { ... } } }`
   reading the secrets, then set `signingConfig = signingConfigs.release` in the
   release build type. Rebuild. From then on releases are signed and updatable
   without uninstalling.

## Versioning

Bump `versionCode`/`versionName` in each module's `build.gradle.kts` before
tagging. Live-trading arming state intentionally resets on version changes;
users must re-arm after reviewing the new version.
