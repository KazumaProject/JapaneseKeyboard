# F-Droid Release Notes

This repository now includes the upstream metadata and build behavior needed to package the `liteFdroid` variant for F-Droid.

## What ships on F-Droid

- Package name: `com.kazumaproject.markdownhelperkeyboard.lite.fdroid`
- Version name: `1.7.25-lite-fdroid`
- Version code: `719`
- Gradle flavor: `liteFdroid`

The F-Droid flavor is the lightweight edition. It does not include the optional `zenz` or `gemma` model features.

## Upstream metadata

F-Droid can read app-store metadata directly from this repository.

- Text metadata: `fastlane/metadata/android/`
- English locale: `fastlane/metadata/android/en-US/`
- Japanese locale: `fastlane/metadata/android/ja-JP/`

The screenshots and icon used for the listing are stored under the `en-US/images/` subtree, which is supported by F-Droid's fastlane-compatible metadata import.

## Local build command

Build the exact unsigned APK that matches the F-Droid submission draft with:

```bash
./gradlew assembleLiteFdroidReleaseUnsigned
```

If your shell points `JAVA_HOME` to a stale JDK path, run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleLiteFdroidReleaseUnsigned
```

The APK is generated at:

```text
app/build/outputs/apk/liteFdroid/releaseUnsigned/
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
2. Ensure the release commit is tagged with the normal upstream tag, for example `v1.7.25`.
3. Add a dedicated F-Droid tag on the same commit, for example `fdroid-v1.7.25-lite-fdroid`.
4. Push the F-Droid tag so `fdroid checkupdates` can track the full suffix-preserving version name.
5. Copy the draft YAML into a fork of `fdroiddata`.
6. Run the `fdroid` validation commands locally or in the official Docker environment.
7. Open a merge request against `F-Droid/fdroiddata`.
The draft uses the existing `releaseUnsigned` build type explicitly and points fdroidserver at the generated APK path.

## Tagging command

Create the dedicated F-Droid tag on the same release commit:

```bash
git tag fdroid-v1.7.25-lite-fdroid v1.7.25
git push origin fdroid-v1.7.25-lite-fdroid
```

The repository's GitHub release workflow only triggers on tags that start with `v`, so the `fdroid-v...` tag avoids creating a duplicate GitHub Release while still giving F-Droid a stable tag to follow.
