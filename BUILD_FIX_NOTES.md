# Build Notes

## Current build configuration

- Android Gradle Plugin: 8.13.1
- Gradle wrapper: included
- Java compatibility: 17
- `compileSdk`: 35
- `targetSdk`: 35
- `minSdk`: 24
- AndroidX enabled
- Main dependencies: AppCompat, Material Components, RecyclerView, ConstraintLayout, Activity, DrawerLayout

## Important Gradle structure

The project uses the modern Gradle layout:

- root `build.gradle` uses `plugins { ... }`
- `settings.gradle` uses `pluginManagement { ... }` and `dependencyResolutionManagement { ... }`
- app configuration is in `app/build.gradle`

Do not reintroduce old `buildscript { ... }` / `allprojects { ... }` repository blocks unless there is a specific reason.

## Clean rebuild

```bash
./gradlew clean assembleDebug
```

Windows:

```powershell
.\gradlew.bat clean assembleDebug
```

## Files that must stay local

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
*.pem
*.p12
.env
secrets.properties
google-services.json
captures/
*.hprof
```

## Notes for GitHub updates

When preparing a public source package, use repository source files only. Exclude IDE metadata, Gradle caches, build output, signing material, and local machine paths.

This clean package already excludes those local/private files.
