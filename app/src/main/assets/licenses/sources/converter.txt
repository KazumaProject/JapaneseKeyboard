# Third-party notices

This release is a combined work. The project source code is MIT, but the
generated dictionary data keeps the license and notice requirements of its
upstream data sources. Do not treat the complete release archive as a single
MIT-licensed work.

## Project source code

The Kotlin conversion program and project-owned source files are distributed
under the MIT License. The complete project license is in the repository
[LICENSE](https://github.com/KazumaProject/kotlin-kana-kanji-converter/blob/main/LICENSE).

## JapaneseCorpus / JMdict English data

The hiragana-to-English source is generated from JMdict_e by
[JapaneseCorpus v2026.0803.6](https://github.com/KazumaProject/JapaneseCorpus/releases/tag/v2026.0803.6).
The source archive is:

<https://github.com/KazumaProject/JapaneseCorpus/releases/download/v2026.0803.6/mozc-english-unigram-00000.txt.zst>

The JMdict-derived database data is licensed under
[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). The
file `licenses/JMDICT-LICENSE.html` in this archive is the authoritative
attribution and license notice for that source data. The corresponding
JapaneseCorpus release is the fixed source snapshot listed above.

This release is an adapted database. Its modifications include selecting
English glosses and complete English `lsource` values, converting the selected
Katakana readings to hiragana, normalizing small `ゕ`/`ゖ` readings, and applying
the noise filter documented in
[`docs/english-dictionary.md`](https://github.com/KazumaProject/kotlin-kana-kanji-converter/blob/main/docs/english-dictionary.md).
Only `primary` candidates are included in the runtime English dictionary;
`review` and `excluded` candidates remain in the quality report for audit.
Redistribution of the JMdict-derived portion must preserve the CC BY-SA 4.0
attribution and ShareAlike requirements.

## Google Mozc resources

The Japanese dictionary and related resources are derived from
[google/mozc](https://github.com/google/mozc). Mozc's code license and binary
redistribution notice are in its
[`LICENSE`](https://github.com/google/mozc/blob/master/LICENSE). The bundled
`src/data/dictionary*` resources are mixed-license data; the complete upstream
notice is included in this archive as `licenses/MOZC-LICENSE`, alongside
`licenses/IPADIC-COPYING` and `licenses/IPADIC-NOTICE`. The upstream source
notice is also available at
[`src/data/dictionary_oss/README.txt`](https://raw.githubusercontent.com/google/mozc/master/src/data/dictionary_oss/README.txt).

The Mozc dictionary notice includes, among other conditions:

- NAIST/IPAdic copyright and attribution requirements;
- the ICOT Free Software no-warranty and notice requirements; and
- Okinawa dictionary public-domain terms.

Those upstream conditions continue to apply to the corresponding generated
dictionary assets in this release. Google/Mozc names are not used to endorse
this project.

## Practical redistribution rule

When redistributing the release archive or extracted dictionary assets, keep
this file and the `licenses/` directory with the assets and preserve the
upstream notices. `licenses/LICENSE-DATA.md` and `licenses/NOTICE.md` document
the JapaneseCorpus data provenance and terms.
License questions about a particular downstream use should be reviewed by a
qualified legal professional.
