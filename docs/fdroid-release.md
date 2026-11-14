# F-Droid releases

The F-Droid package is `com.kazumaproject.markdownhelperkeyboard.lite.fdroid`
(`liteFdroid`). It excludes the optional Zenz and local AI runtimes and models.
Its version is taken from `app/build.gradle`, with the `-lite-fdroid` suffix.

## Publication and official availability

The [GitHub release workflow](../.github/workflows/android.yml) publishes
`app-liteFdroid-release.apk` for a new `v*` tag only after it:

1. Builds the exact workflow commit twice, on separate runners with empty Gradle
   user homes and no restored build or configuration caches.
2. Checks each APK's embedded source revision and dictionary packaging.
3. Requires the unsigned APKs to be byte-for-byte identical.
4. Signs the verified APK, preserving alignment, and verifies signature copying
   against the independent unsigned rebuild with `apksigcopier`.
5. Checks that the public tag still resolves to the workflow commit.

Relevant pull requests also run the unsigned F-Droid builds, dictionary decoder
tests, and byte comparison. Signing and publication run on tag pushes or manual
workflow dispatches; manual dispatch does not create a GitHub Release.

A successful GitHub workflow does not establish that the official F-Droid
repository has accepted or distributed that version. Check these separately:

- [Official package listing](https://f-droid.org/packages/com.kazumaproject.markdownhelperkeyboard.lite.fdroid/)
- [Official package version API](https://f-droid.org/api/v1/packages/com.kazumaproject.markdownhelperkeyboard.lite.fdroid)
- [Current fdroiddata metadata](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/com.kazumaproject.markdownhelperkeyboard.lite.fdroid.yml)
- [Upstream reproducibility issue](https://github.com/KazumaProject/JapaneseKeyboard/issues/1293)

The file under this repository's `fdroid/metadata/` is the historical submission
draft. The app is already in fdroiddata; use its current metadata when validating
an update, rather than replacing it with the old draft.

Never move an existing release tag or replace published APK assets to deliver
a fix. Publish a new version and tag so F-Droid rebuilds the same source as the
reference binary.

## Rebuild a release

Use a fresh clone of the exact public tag. Install the SDK platform, build tools,
and NDK listed in the release workflow. The Gradle wrapper and
`gradle/gradle-daemon-jvm.properties` select the Gradle and daemon JDK versions.
Keep signing properties out of this checkout when building the unsigned APK.

```bash
git clone https://github.com/KazumaProject/JapaneseKeyboard.git
cd JapaneseKeyboard
git checkout --detach v<VERSION>
export GRADLE_USER_HOME="$(mktemp -d)"
./gradlew clean :app:assembleLiteFdroidRelease \
  --no-build-cache --no-configuration-cache --no-daemon --max-workers=1
python3 tools/release/verify-release-source.py \
  --revision "$(git rev-parse HEAD)" --tag v<VERSION> \
  app/build/outputs/apk/liteFdroid/release/app-liteFdroid-release.apk
python3 tools/release/verify-lite-apk.py \
  app/build/outputs/apk/liteFdroid/release/app-liteFdroid-release.apk
```

Compare the release APK with this unsigned rebuild using the pinned
`apksigcopier` version and signing tools from the workflow:

```bash
apksigcopier compare --unsigned /path/to/published/app-liteFdroid-release.apk \
  app/build/outputs/apk/liteFdroid/release/app-liteFdroid-release.apk
```

The [F-Droid reproducible-build documentation](https://f-droid.org/docs/Reproducible_Builds/)
describes environment differences that can affect this comparison. Run
`fdroid readmeta`, `fdroid lint`, and `fdroid build` against the current fdroiddata
checkout to validate the official build recipe as well.

## Lite APK size

Lite still bundles the Japanese and English dictionaries, classified dictionaries,
English grammar data, and precomputed search indexes. Excluding AI features does
not remove those assets.

The system dictionary, English arrays, English lookup index, and grammar index
are stored uncompressed because runtime readers use `AssetManager.openFd()` and
memory mapping. English trie labels use a small alphabet and one-byte indices in
the APK; the reader restores the original UTF-16 `CharArray`, including non-ASCII
characters. Alphabets larger than 256 characters use the raw UTF-16 format.
No extracted dictionary cache is needed.

Release variants package the generated dictionaries instead of also shipping the
old English and Japanese system dictionary files. Connection matrix assets are
read through streams and can use APK compression. `verify-lite-apk.py` checks
these packaging rules and rejects native libraries or AI model files in Lite.

## Store metadata

F-Droid imports the listing text from `fastlane/metadata/android/en-US/` and
`fastlane/metadata/android/ja-JP/`, and the listing images from the existing
`en-US/images/` subtree.
