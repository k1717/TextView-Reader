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

## Web upload / replacement flow

1. Unzip this package.
2. Open the unzipped folder.
3. Go **inside** `TextView-Reader_GitHub_SAFE_UPDATE/`.
4. Select the contents of that folder, such as `app/`, `gradle/`, `.gitignore`, `README.md`, `LICENSE`, `build.gradle`, and `settings.gradle`.
5. On GitHub, open the `k1717/TextView-Reader` repository root.
6. Click **Add file > Upload files**.
7. Drag the selected contents into the upload page.
8. Commit with a message such as:

```text
Update reader fixes and documentation
```

Do not upload the outer package folder itself, or GitHub will create an extra nested directory.

## Important replacement warning

GitHub web upload overwrites files with matching paths, but it does not automatically delete old files that are no longer in this package. If the repository already contains old generated files such as `.idea/`, `.gradle/`, `app/build/`, APKs, or `local.properties`, delete those files/directories from GitHub and commit the deletion separately.

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

## Sanity checks before commit

```bash
git status --short
find . -name local.properties -o -name "*.apk" -o -name "*.aab" -o -name "*.jks" -o -name "*.keystore"
```

The second command should not list anything that you intend to commit.
