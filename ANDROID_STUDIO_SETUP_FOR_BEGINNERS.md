# Android Studio Setup for Beginners

This guide explains how to open and build TextView Reader.

## 1. Open the correct folder

Open the repository root folder in Android Studio.

Correct:

```text
TextView-Reader/
```

Do not open only:

```text
TextView-Reader/app/
```

## 2. Let Gradle sync

Android Studio should start Gradle sync automatically.

If it does not, click:

```text
File > Sync Project with Gradle Files
```

## 3. Install missing SDK components

If Android Studio asks to install SDK Platform 35 or Build Tools, allow it.

## 4. Check Java version

Use JDK 17.

In Android Studio:

```text
File > Settings > Build, Execution, Deployment > Build Tools > Gradle
```

Set Gradle JDK to a JDK 17 installation.

## 5. Build the app

Use:

```text
Build > Make Project
```

or run:

```bash
./gradlew assembleDebug
```

## 6. Run on a device

Connect an Android device or start an emulator, then press **Run**.

## Current version

```text
TextView Reader 2.0.3
```

## What not to upload to GitHub

Do not upload generated or local files:

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
