# GitHub Upload Notes

This repository should contain only source files and public project documentation.

## Do not upload

```text
.gradle/
.idea/
build/
app/build/
local.properties
*.iml
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

`local.properties` is machine-specific. Android Studio recreates it with the local SDK path.

## Correct project layout

The Android app source should be under:

```text
app/src/main/AndroidManifest.xml
app/src/main/java/
app/src/main/res/
app/src/main/ic_launcher-playstore.png
```

The repository root should not contain these Android source/resource duplicates:

```text
java/
res/
AndroidManifest.xml
ic_launcher-playstore.png
```

If those root-level duplicates are already visible on GitHub, delete them from GitHub and commit the deletion.

Suggested deletion commit message:

```text
Remove accidentally uploaded root Android source duplicates
```

## Web upload / replacement flow

1. Unzip the clean upload package.
2. Open the unzipped folder.
3. Go **inside** `TextView-Reader_GitHub_SAFE_UPDATE/`.
4. Select the contents of that folder, such as:
   - `app/`;
   - `gradle/`;
   - `.gitignore`;
   - `README.md`;
   - `LICENSE`;
   - `build.gradle`;
   - `settings.gradle`.
5. On GitHub, open the `k1717/TextView-Reader` repository root.
6. Click **Add file > Upload files**.
7. Drag the selected contents into the upload page.
8. Commit with a message such as:

```text
Update reader fixes and documentation
```

Do not upload the outer package folder itself, or GitHub will create an extra nested directory.

## Important replacement warning

GitHub web upload overwrites files with matching paths, but it does not automatically delete old files that are no longer in the uploaded package.

If the repository already contains old generated files or accidental duplicate files, delete those files/directories from GitHub and commit the deletion separately.

Check especially for:

```text
.idea/
.gradle/
build/
app/build/
local.properties
*.apk
*.aab
java/
res/
AndroidManifest.xml
ic_launcher-playstore.png
```

## Command-line alternative

```bash
git clone https://github.com/k1717/TextView-Reader.git
cd TextView-Reader

# Copy the contents of TextView-Reader_GitHub_SAFE_UPDATE/ here.
# Do not copy .gradle, .idea, build outputs, APKs, keys, or local.properties.

git status
git add .
git commit -m "Update reader fixes and documentation"
git push origin main
```

If root-level duplicate Android files exist, remove them before committing:

```bash
git rm -r java res
git rm AndroidManifest.xml ic_launcher-playstore.png
git commit -m "Remove accidentally uploaded root Android source duplicates"
git push origin main
```

## Sanity checks before commit

```bash
git status --short
find . -name local.properties -o -name "*.apk" -o -name "*.aab" -o -name "*.jks" -o -name "*.keystore"
```

The second command should not list anything that you intend to commit.
