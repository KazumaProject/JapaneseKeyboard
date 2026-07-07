# F-Droid Release Notes

This repository now includes the upstream metadata and build behavior needed to package the `liteFdroid` variant for F-Droid.

## What ships on F-Droid

- Package name: `com.kazumaproject.markdownhelperkeyboard.lite.fdroid`
- Version name: `1.7.27`
- Version code: `721`
- Gradle flavor: `liteFdroid`

The F-Droid flavor is the lightweight edition. It does not include the optional `zenz` or `gemma` model features.

## Upstream metadata

F-Droid can read app-store metadata directly from this repository.

- Text metadata: `fastlane/metadata/android/`
- English locale: `fastlane/metadata/android/en-US/`
- Japanese locale: `fastlane/metadata/android/ja-JP/`

The screenshots and icon used for the listing are stored under the `en-US/images/` subtree, which is supported by F-Droid's fastlane-compatible metadata import.

## Local build command

Build the exact release APK that matches the F-Droid submission draft with:

```bash
./gradlew assembleLiteFdroidRelease
```

If your shell points `JAVA_HOME` to a stale JDK path, run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleLiteFdroidRelease
```

The APK is generated at:

```text
app/build/outputs/apk/liteFdroid/release/app-liteFdroid-release.apk
```

## fdroiddata draft

A starter metadata file for submitting this app to the main F-Droid repository is included at:

```text
fdroid/metadata/com.kazumaproject.markdownhelperkeyboard.lite.fdroid.yml
```

Copy that file into your `fdroiddata/metadata/` checkout, then run the standard validation flow:

```bash
fdroid readmeta
fdroid lint com.kazumaproject.markdownhelperkeyboard.lite.fdroid
fdroid checkupdates --allow-dirty com.kazumaproject.markdownhelperkeyboard.lite.fdroid
fdroid build com.kazumaproject.markdownhelperkeyboard.lite.fdroid
```

## Submission checklist

1. Push the upstream metadata changes to GitHub.
2. Ensure the release commit is tagged with the normal release tag, for example `v1.7.27`.
3. Let the GitHub Release workflow upload `app-liteFdroid-release.apk` for that tag.
4. Copy the draft YAML into a fork of `fdroiddata`.
5. Run the `fdroid` validation commands locally or in the official Docker environment.
6. Open a merge request against `F-Droid/fdroiddata`.
The draft points fdroidserver at the universal F-Droid APK and keeps `Binaries` on the versioned GitHub Release asset.

## Tagging command

Create the release tag:

```bash
git tag v1.7.27
git push origin v1.7.27
```

The repository's GitHub release workflow triggers on tags that start with `v`, so this tag is also where the `Binaries` URL resolves.
