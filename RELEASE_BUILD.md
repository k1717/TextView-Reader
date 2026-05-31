# Public release build checklist

This source package is prepared for public GitHub distribution of TextView Reader 2.2.1.
The app version metadata remains:

```text
versionCode 2210
versionName "2.2.1"
```

## Keystore policy

Do not commit release signing files or passwords. The release build reads signing values from environment variables first and then from `~/.gradle/gradle.properties`:

```text
TEXTVIEW_KEYSTORE_PATH=/secure/path/release.keystore
TEXTVIEW_KEYSTORE_PASSWORD=...
TEXTVIEW_KEY_ALIAS=textview
TEXTVIEW_KEY_PASSWORD=...
```

Create the release keystore once and back it up securely:

```bash
keytool -genkeypair \
  -alias textview \
  -keyalg RSA -keysize 4096 \
  -validity 36500 \
  -keystore release.keystore \
  -dname "CN=your-alias, OU=Apps, O=your-project-name, L=City, ST=State, C=Country"
```

The distinguished name is visible in the APK certificate. Use values you are comfortable publishing.
Losing this keystore prevents normal updates to the same installed app package.

## Build commands

```bash
./gradlew clean
./gradlew testDebugUnitTest
./gradlew compileReleaseJavaWithJavac
./gradlew assembleRelease
```

The signed APK is expected at:

```text
app/build/outputs/apk/release/app-release.apk
```

## Release verification

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk

aapt dump xmltree app/build/outputs/apk/release/app-release.apk AndroidManifest.xml \
  | grep -E "debuggable|usesCleartextTraffic|INTERNET"

strings app/build/outputs/apk/release/app-release.apk \
  | grep -E "^/(home|Users|builds)/" | sort -u

sha256sum app/build/outputs/apk/release/app-release.apk
```

Expected results:

- certificate is your release certificate, not `CN=Android Debug, O=Android, C=US`;
- `debuggable` is absent or false;
- `usesCleartextTraffic` is false;
- no `INTERNET` permission is present;
- no build-machine path appears in the APK strings output;
- the APK SHA-256 can be copied into the GitHub Release notes.

## Public-source notes

- Upload the contents of the extracted project folder, not the outer extracted folder itself.
- `*.keystore`, `*.jks`, `local.properties`, and build outputs are ignored by git.
- R8/minify and resource shrinking are enabled for release builds.
- JUniversalChardet remains active for TXT encoding detection.
- Network cleartext is disabled, and WebView resource interception explicitly blocks non-local requests.

## Pre-upload source check

Before uploading through the GitHub web UI, confirm the extracted source tree does not include:

```text
.gradle/
.idea/
build/
app/build/
app/src/androidTest/assets/large_txt_real_fixture.txt
local.properties
*.apk
*.aab
*.apks
*.jks
*.keystore
*.pem
*.p12
.env
.env.*
secrets.properties
google-services.json
captures/
*.hprof
*.log
```
