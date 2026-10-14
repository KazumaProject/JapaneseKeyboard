# JapaneseCorpus generated data notice

## Japanese Wikipedia

- Source: <https://dumps.wikimedia.org/other/cirrus_search_index/>
- Project: <https://ja.wikipedia.org/>
- License information: <https://dumps.wikimedia.org/legal.html>
- Applicable license for this distribution: CC BY-SA 4.0
- Copyright: Wikimedia Foundation and Wikipedia contributors

The corpus contains normalized article text, titles, stable page URLs, and
source timestamps. It does not contain images.

## Aozora Bunko

- Source: <https://www.aozora.gr.jp/>
- Official metadata: <https://www.aozora.gr.jp/index_pages/person_all.html>
- File-handling guidelines: <https://www.aozora.gr.jp/guide/kijyunn.html>
- Official source repository: <https://github.com/aozorabunko/aozorabunko>

Only works whose official work and person copyright flags are all `なし` are
included. The exact repository commit and metadata archive checksum are recorded in
each Release manifest.

## Pipeline inspiration

The source selection and future kana-kanji conversion use case were informed by
[Akaza](https://github.com/akaza-im/akaza), an MIT-licensed Japanese input
method. JapaneseCorpus has its own extraction and packaging implementation.

## Mozc context IDs and dictionary format

- Source: <https://github.com/google/mozc>
- Pinned commit: `3f235b4eb6fcff7d14ef5f0fb8ee56de7ee4c732`
- Included file: `src/data/dictionary_oss/id.def` as Release asset `mozc-id.def`
- License: the Mozc and dictionary notices in the included `MOZC-LICENSE`

The generated dictionaries use the five-column text format consumed by Mozc's
system dictionary loader. Mozc itself and its original word dictionary are not
redistributed, apart from the matching context-ID definition and required notice.

## Vibrato and IPADIC

- Vibrato: <https://github.com/daac-tools/vibrato>, version 0.5.2, MIT license
- Dictionary archive: Vibrato release v0.5.0, `ipadic-mecab-2_7_0`
- IPADIC: version 2.7.0, NAIST/ICOT terms

Vibrato and IPADIC are used for morphological analysis and readings. The Release
includes `IPADIC-COPYING` and `IPADIC-NOTICE` alongside the generated dictionaries.

## JMdict Japanese-English dictionary

- Project: Electronic Dictionary Research and Development Group JMdict/EDICT
- Source: <https://www.edrdg.org/pub/Nihongo/JMdict_e.gz>
- Documentation: <https://www.edrdg.org/wiki/JMdict-EDICT_Dictionary_Project.html>
- Licence: CC BY-SA 4.0 and the attribution conditions published by EDRDG

The hiragana-to-English dictionary extracts English glosses and complete
English language-source forms from katakana-reading JMdict entries. Each
Release includes the exact source snapshot, its SHA-256 in the English
dictionary manifest, and a saved copy of the EDRDG licence page.

## AJIMEE-Bench

- Project: <https://github.com/azooKey/AJIMEE-Bench>
- Pinned commit: `401666cd56d1a570c2021798b64b6da4396bfd45`
- Dataset: `JWTD_v2/v1/evaluation_items.json`
- Dataset SHA-256: `e9eb668fd6aa14b1e26436f429b5550108af0a1dfd443b8cea0bcb3ab3028fca`
- Dataset licence: CC BY-SA 3.0
- Evaluation implementation licence: CC0 1.0

The pinned dataset is downloaded and checksum-verified during CI and Release
builds, but is not redistributed by JapaneseCorpus. A Release contains only an
aggregate report with Accuracy@1 and MinCER values, source provenance, and no
benchmark prompts or expected outputs.

## Pipeline code

The source code and configuration in this repository are licensed under the
MIT License. Generated corpus files are covered by `LICENSE-DATA.md`.
