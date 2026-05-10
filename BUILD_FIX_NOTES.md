# Build Fix Notes

This project uses the modern Android Gradle setup:

- Android Gradle Plugin: 8.13.1
- Gradle wrapper: 9.0.0
- Compile SDK: 35
- Target SDK: 35
- Min SDK: 24
- Java compatibility: 17

Current app version:

```gradle
versionCode 203
versionName "2.0.3"
```

## Build

Open the project root folder in Android Studio, then click **Sync Now**.

If Android Studio asks to install SDK Platform 35 or Build Tools, click **Install**.

Command-line build:

```bash
./gradlew assembleDebug
```

## Common cleanup before committing

Do not commit:

```text
.gradle/
.idea/
build/
app/build/
local.properties
*.apk
*.aab
*.jks
*.keystore
.env
secrets.properties
google-services.json
```

## Project-root warning

Open the repository root folder. Do not open only the `app/` folder.

The correct root contains:

```text
app/
gradle/
build.gradle
settings.gradle
gradle.properties
gradlew
gradlew.bat
```
