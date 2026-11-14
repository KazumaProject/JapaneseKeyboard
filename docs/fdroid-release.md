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

Release APKs store the six memory-mapped dictionaries and indexes as XZ streams.
The encoder and decoder both use the pinned pure-Java `org.tukaani:xz:1.12`;
the build emits an asset manifest with the uncompressed sizes and SHA-256 hashes.
It includes no timestamps or machine paths.

On first use of an asset, the app verifies and atomically writes a read-only copy
to its private `noBackupFilesDir/bundled-dictionaries/` directory, then uses the
existing memory-mapped reader. Later processes reuse copies whose content-addressed
name and size match the packaged manifest. Changed assets replace obsolete copies
under a cross-process file lock. The cache is not included in backups.

This reduces APK/download size. Expanding all six assets requires about 65.73 MB
of additional private storage and adds work on first use after a dictionary change.
If a cache write fails, the reader decompresses and verifies the asset in memory;
that fallback uses more heap until its source buffer is no longer needed (lookup
bounds and grammar retain their backing buffers).

Debug variants retain directly mappable assets. English trie labels use a small
alphabet and one-byte indices; the reader restores the original UTF-16 arrays.
Alphabets larger than 256 characters use raw UTF-16.

Release variants exclude duplicate legacy Japanese system and English dictionary
files. Streamed connection matrices use APK compression. `verify-lite-apk.py`
decodes all six XZ streams, checks their sizes, hashes and format headers, and
rejects duplicate or unexpected native/model assets.

## Store metadata

F-Droid imports the listing text from `fastlane/metadata/android/en-US/` and
`fastlane/metadata/android/ja-JP/`, and the listing images from the existing
`en-US/images/` subtree.
