# Optional RAR5 decoder

`Rar5LibraryFallback` loads RealBurst/unrar5j by reflection. The normal app build does not require this jar, but compressed RAR5 extraction only becomes active when the jar is present here.

## Add the jar

From the project root:

```bash
./gradlew :app:fetchUnrar5jJar
```

That task saves the jar as:

```text
app/libs/unrar5j-v1.0.3.jar
```

The task first tries the versioned release-asset name and then falls back to the upstream `unrar5j.jar` asset name while saving it under the versioned local filename.

Manual alternative: download the `unrar5j-v1.0.3.jar` or `unrar5j.jar` release asset from RealBurst/unrar5j and place it in this directory.

## Why local jar instead of a normal Maven dependency?

The upstream project is distributed as source/release jar from GitHub and currently does not expose a Maven Central coordinate. Keeping it optional avoids breaking offline builds or builds that do not need RAR5 compressed extraction.

## Current scope

When the jar is present, the app attempts RAR5 listing and extraction through unrar5j for compressed, encrypted, and solid RAR5 archives. Upstream still marks multi-volume RAR5 support as partial, so split RAR5 archives are still not treated as guaranteed.

## Notice requirement

Keep the unrar5j Apache-2.0 notice with release materials when the optional jar is distributed. The project-level `THIRD_PARTY_NOTICES.md` records the optional use boundary.
