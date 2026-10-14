# Verification results

Base: `dev` at `100391ee31058923f7024547a23e1de243036b53`.
Distribution: `v1.7.262-pre.1`, source revision
`574d450c652e42e81713cc1c926a57c3f5310320`.

- Release archive SHA-256:
  `1f5248c48689be664e78aad02ad316f44810a1bd4ccb16b54ef087177eaa2d05`.
- `prepare.py --check`: all 117 published assets, notice snapshots, and the
  generated surface-repair sidecar checksum passed.
- 158 relevant Lite JVM/Robolectric unit tests passed, with no skips.
- Lite Standard debug APK/test APK and Full Standard debug APK builds passed.
- Pixel 6, Android API 37, isolated package
  `com.kazumaproject.markdownhelperkeyboard.classifiedprobe.lite`:
  six instrumented tests passed together.

The device tests decode all 767,274 records and compare counts and SHA-256
multiset fingerprints for reading, surface, left/right POS IDs and cost with the
frozen input. One source-derived literal surface repair is included; raw release
binary assets are unchanged. Before the repair, exactly one Place record differed:
`あとこしゃらか / アトコシヤラカ` decoded as `アトコシャラカ`.

Category tests exercise all 28 categories with and without bunsetsu separation,
legacy/incremental backends, default/conversion/prediction query modes, and
category enable/disable changes on the same session. Prefix prediction is tested.
The settings test calls the actual switch change handlers, checks persistence,
and waits for the refreshed screen to show both states for every category.
License dialogs for all 28 categories render offline. Injected touches through
the actual Romaji keyboard produce the `[賞]` label; clicking its candidate
commits text through the editor's InputConnection. The prior default IME is
restored. The updated n-gram/unigram assets load and match on ART.

Scope limits: Full was built but not exercised on the device. Other Android
versions, every possible sentence/ranking, and the accuracy of upstream semantic
classifications were not verified. Original acquisition revisions and per-record
copyright attribution are unconfirmed and are labelled as such in the notices.
