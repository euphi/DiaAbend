# Releasing

## Version scheme

- `versionName` = git tag without the `v` (tag `v0.1.0` -> `0.1.0`).
- `versionCode` = `MAJOR*10000 + MINOR*100 + PATCH` (0.1.0 -> 100, 1.2.3 -> 10203).
- Release candidates `vX.Y.Z-rcN`: `versionName` = `X.Y.Z-rcN`, `versionCode` =
  the code of the coming final version (0.1.0-rc1 -> 100). The final version
  keeps the same code; Android allows updating to an equal `versionCode`, and
  F-Droid only sees final tags.
- Both live in [`app/build.gradle.kts`](app/build.gradle.kts). The release
  workflow fails if the tag doesn't match `versionName`.

## Making a release

1. Bump `versionName` and `versionCode` in `app/build.gradle.kts`.
2. Add changelogs (max. 500 characters each):
   `fastlane/metadata/android/{en-US,de}/changelogs/<versionCode>.txt`.
   They become the GitHub release text and F-Droid's "What's new".
3. Commit, tag, push:
   ```
   git tag v0.1.0
   git push origin main v0.1.0
   ```

[`.github/workflows/release.yml`](.github/workflows/release.yml) then runs the
unit tests, builds the signed APK and publishes it as a GitHub release
(`DiaAbend-vX.Y.Z.apk`). Tags containing `-rc` or `-beta` are marked as
pre-release.

## Signing key

The release key is a PKCS12 keystore, alias `diaabend`, certificate
`CN=Ian Hubbertz`, SHA-256 fingerprint:

```
f018ee5059769d43e5acaedbfa8298bbe2f6302d6c530dda0925b82e83d9280f
```

The workflow gets it from four repository secrets: `ANDROID_KEYSTORE_BASE64`
(the keystore, base64-encoded), `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Without them, tag pushes fail on
purpose and manual runs produce an unsigned APK.

**The keystore must never be lost or replaced.** Without it there are no more
updates, neither on F-Droid nor for anyone who installed the GitHub APK. Keep
a backup outside this machine; it is not in the repository.

Check a release APK:
```
apksigner verify --print-certs DiaAbend-v0.1.0.apk | grep SHA-256
```

## F-Droid

F-Droid reads the store texts, icon and changelogs directly from
[`fastlane/metadata/android/`](fastlane/metadata/android/). The build recipe
for fdroiddata is [`fdroid/org.diaabend.yml`](fdroid/org.diaabend.yml)
(passes `fdroid lint` and `fdroid rewritemeta` unchanged).

### Reproducible build

F-Droid builds each version itself from the tag, downloads our APK from the
GitHub release (`Binaries:` in the recipe) and compares the two. If they are
identical apart from the signature, F-Droid publishes **our** APK with our
signature, so F-Droid and GitHub installs can update each other. If a single
byte differs, F-Droid doesn't publish that version.

What keeps the builds identical:
- JDK 21 in CI, like F-Droid's build server (Debian trixie `default-jdk`).
- Gradle from the wrapper (`gradle/wrapper/gradle-wrapper.properties`), which
  F-Droid uses too.
- No dependency info blob (`dependenciesInfo` in `app/build.gradle.kts`) and
  no baseline profile (the `ArtProfile` tasks are disabled).

Upgrading JDK, Gradle or AGP: keep CI and F-Droid's side in mind.

Testing reproducibility locally (needs `pip install apksigcopier`):
```
git checkout v0.1.0
./gradlew clean assembleRelease      # without ANDROID_KEYSTORE_PATH -> unsigned
apksigcopier compare DiaAbend-v0.1.0.apk \
  --unsigned app/build/outputs/apk/release/app-release-unsigned.apk && echo reproducible
```

### First submission (once, for the first final version)

Release candidates can't go to F-Droid: `UpdateCheckMode` only accepts final
tags `vX.Y.Z`. Once `v0.1.0` is tagged and its GitHub release exists:

1. Fork <https://gitlab.com/fdroid/fdroiddata>, create a branch `org.diaabend`.
2. Copy `fdroid/org.diaabend.yml` to `metadata/org.diaabend.yml` in the fork.
   Replace `commit: v0.1.0` with the full commit hash
   (`git rev-parse v0.1.0^{commit}`); the maintainers require the hash.
   **Don't add comments**: the CI checks that the file looks exactly like
   `fdroid rewritemeta` writes it, which strips comments.
3. Push. The fork's CI runs `fdroid lint` and a test build (including the
   reproducibility check). When it's green, open a merge request against
   `fdroid/fdroiddata` with the "App inclusion" template.
4. Wait for review (days to a few weeks). After the merge it takes another
   index build (~1-2 days) until the app shows up in the client.

Answers to likely reviewer questions:
- **Anti-features:** none. No tracking, no proprietary dependencies
  (AndroidX, Kotlin coroutines, jcifs-ng with Bouncy Castle). Network access
  only to SMB servers in the local network.
- **Fire TV:** the app targets Fire OS (minSdk 22) but has no Amazon
  dependencies; it is a plain Android app with a leanback launcher entry.

### Later releases

Nothing to do on F-Droid's side: the checkupdates bot finds new `vX.Y.Z` tags
(`UpdateCheckMode: Tags`) and adds the build entry itself
(`AutoUpdateMode: Version`).
