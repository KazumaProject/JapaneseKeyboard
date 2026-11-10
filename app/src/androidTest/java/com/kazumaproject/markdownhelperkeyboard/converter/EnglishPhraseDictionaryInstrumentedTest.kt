package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.converter.english.phrase.EnglishPhraseDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.english.phrase.PhraseConverter
import com.kazumaproject.markdownhelperkeyboard.converter.english.phrase.RuntimeGrammarRoles
import com.kazumaproject.markdownhelperkeyboard.converter.english.phrase.phraseContinuations
import com.kazumaproject.markdownhelperkeyboard.converter.engine.EnglishEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlidePrebuiltDictionaryLoader
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlidePrebuiltLoadResult
import com.kazumaproject.markdownhelperkeyboard.converter.session.CandidateQueryMode
import com.kazumaproject.markdownhelperkeyboard.converter.session.ConversionBackend
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiConversionSession
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiQueryRequest
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryBinaryReader
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileKey
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileSpecs
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideStore
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionarySourceResolver
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.ObjectInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EnglishPhraseDictionaryInstrumentedTest {
    @Test
    fun registeredPhrasesOfferOnlyTheContinuationAfterACommittedWord() = withDictionary { _, _, reader ->
        val engine = EnglishEngine().apply { configureLazyDictionaryLoading(reader) }
        val registered = listOf(
            "Ariana" to "Grande",
            "Elon" to "Musk",
            "Taylor" to "Swift",
            "Android" to "Studio",
            "Google" to "Maps",
            "Toyota" to "Corolla",
            "New" to "York",
            "New York" to "Post",
            "good" to "morning",
            "ice" to "cream",
            "machine" to "learning",
            "coffee" to "shop",
            "two-factor" to "authentication",
        )
        for ((prefix, suffix) in registered) {
            val continuations = engine.getPhraseContinuations(prefix)
            assertTrue("$prefix: $continuations", suffix in continuations)
            assertEquals(prefix, continuations, engine.getPhraseContinuations(prefix.lowercase()))
        }
        assertFalse(engine.getPhraseContinuations("Arian").contains("Grande"))
        assertTrue(engine.getPhraseContinuations("Ariana ").isEmpty())
        assertFalse(engine.getPhraseContinuations("Ariana Grande").contains("Grande"))
        assertTrue(engine.getPhraseContinuations("qzxjvk").isEmpty())
        assertTrue(engine.getConversionCandidates("arianagrande", "arianagrande", false, true, false, emptyList())
            .any { it.commitText == "Ariana Grande" })

        val indexed = checkNotNull(reader.loadBundledEnglishForEngine())
        val unindexed = indexed.copy(phraseCompletionBounds = null)
        for (prefix in listOf("ariana", "android", "john", "the", "new", "san")) {
            val continuations = indexed.phraseContinuations(prefix, limit = 48)
            assertEquals(prefix, unindexed.phraseContinuations(prefix, limit = 48), continuations)
            assertEquals(prefix, continuations.distinctBy { it.lowercase() }, continuations)
            assertTrue(prefix, continuations.size <= 48)
        }
    }

    @Test
    fun bundledEnglishArraysMatchLegacyDictionary() = withDictionary { _, resolver, reader ->
        val dictionary = checkNotNull(reader.loadBundledEnglishForEngine())
        fun withLegacyFile(key: DictionaryFileKey, check: (ObjectInputStream) -> Unit) {
            resolver.openBundledForKey(key).use { asset ->
                reader.openZipAwareObjectInputStream(asset, key.name).use(check)
            }
        }

        withLegacyFile(DictionaryFileKey.ENGLISH_READING) { legacy ->
            val reading = dictionary.readingTrie
            assertEquals(legacy.readObject(), reading.LBS)
            assertEquals(legacy.readObject(), reading.isLeaf)
            assertArrayEquals(legacy.readObject() as CharArray, reading.labels)
            assertArrayEquals(legacy.readObject() as IntArray, reading.termIdsSaved)
        }
        withLegacyFile(DictionaryFileKey.ENGLISH_WORD) { legacy ->
            val word = dictionary.wordTrie
            assertEquals(legacy.readObject(), word.LBS)
            assertEquals(legacy.readObject(), word.isLeaf)
            assertArrayEquals(legacy.readObject() as CharArray, word.labels)
        }
        withLegacyFile(DictionaryFileKey.ENGLISH_TOKEN) { legacy ->
            val tokens = dictionary.tokenArray
            assertArrayEquals(legacy.readObject() as ShortArray, tokens.getWordCosts())
            assertArrayEquals(legacy.readObject() as IntArray, tokens.getNodeIds())
            assertEquals(legacy.readObject(), tokens.bitvector)
        }
    }

    @Test
    fun ordinaryWordsDoNotExpandToUnrelatedCatalogNames() = withDictionary { _, _, reader ->
        val engine = EnglishEngine().apply { configureLazyDictionaryLoading(reader) }
        fun legacy(input: String, typo: Boolean = false) =
            engine.getCandidates(input, enableTypoCorrection = typo).map { it.commitText }

        for ((input, excluded) in listOf(
            "hello" to listOf("Hello82", "Hello.app", "HelloFresh", "Hello Games"),
            "taken" to listOf("Takenjam"),
            "about" to listOf("About.me", "About.com"),
            "computer" to listOf("Computer.com"),
            "went" to listOf("Wente", "Wentzel"),
            "yes" to listOf("YES24eBook", "YESCO"),
        )) {
            for (typed in listOf(input, input.replaceFirstChar(Char::uppercaseChar), input.uppercase())) {
                for (typo in listOf(false, true)) {
                    val legacyCandidates = legacy(typed, typo)
                    val conversion = engine.getConversionCandidates(typed, typed, typo, true, true, emptyList())
                        .map { it.commitText }
                    for (candidates in listOf(legacyCandidates, conversion)) {
                        assertEquals(typed, candidates.first())
                        assertFalse("$typed: $candidates", excluded.any { name ->
                            candidates.any { it.equals(name, ignoreCase = true) }
                        })
                    }
                }
            }
        }

        for ((input, expected) in listOf(
            "takenj" to "Takenjam", "takenjam" to "Takenjam",
            "hello8" to "Hello82", "hello82" to "Hello82",
            "helloa" to "Hello.app", "helloapp" to "Hello.app",
            "hello.a" to "Hello.app",
        )) {
            assertTrue("$input: ${legacy(input)}", expected in legacy(input))
        }
    }

    @Test
    fun englishCompletionQualityAcrossConversionSessions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
        val engine = entry.kanaKanjiEngine()
        val repository = entry.userDictionaryRepository()
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(engine, backend)
            for (mode in listOf(CandidateQueryMode.PREDICTION, CandidateQueryMode.NO_TAB_DEFAULT)) {
                suspend fun query(input: String, enabled: Boolean = true): List<String> = session.query(
                    KanaKanjiQueryRequest(
                        input = input,
                        mode = mode,
                        bunsetsuSeparation = false,
                        n = 4,
                        userDictionaryRepository = repository,
                        learnRepository = null,
                        omissionSearchEnabled = false,
                        typoCorrectionJapaneseFlickEnabled = false,
                        typoCorrectionQwertyEnglishEnabled = false,
                        typoCorrectionOffsetScore = 3000,
                        omissionSearchOffsetScore = 1900,
                        beamWidth = 20,
                        predictionConfig = PredictionConfig(englishPredictionEnabled = enabled),
                    ),
                ).candidates.map { it.commitText }

                for ((input, expected) in listOf(
                    "hello" to "Hello", "taken" to "Taken", "hell" to "hello",
                    "i said hello" to "I said hello", "he tak" to "he takes",
                    "takenj" to "Takenjam", "hello8" to "Hello82", "helloapp" to "Hello.app",
                    "hello g" to "Hello Games", "opena" to "OpenAI", "auto" to "AutoCAD",
                    "don'" to "don't", "androidstudio" to "Android Studio",
                )) {
                    val candidates = query(input)
                    assertTrue("$backend $mode $input: $candidates", expected in candidates)
                }
                for (input in listOf("hello", "taken", "have taken", "i said hello")) {
                    val candidates = query(input)
                    assertFalse("$backend $mode $input: $candidates", candidates.any { candidate ->
                        listOf("Hello82", "Hello.app", "Takenjam", "Hello Games").any {
                            candidate.endsWith(it, ignoreCase = true)
                        }
                    })
                }
                assertEquals(listOf("taken", "Taken", "TAKEN"), query("taken").distinct().take(3))
                assertFalse("Takenjam" in query("takenj", enabled = false))
            }
        }
    }

    @Test
    fun auditEnglishCompletionCorpus() = withDictionary { _, _, reader ->
        val data = checkNotNull(reader.loadBundledEnglishForEngine())
        val words = EnglishPhraseDictionary(data, checkNotNull(reader.loadBundledEnglishGrammar()))
        val engine = EnglishEngine().apply { configureLazyDictionaryLoading(reader) }
        val corpus = EnglishCompletionCorpus(data, words)
        val ordinary = corpus.ordinary
        val partial = corpus.partial
        assertTrue("Expected broad word coverage", ordinary.size >= 6_000)
        assertTrue("Expected broad prefix coverage", partial.size >= 11_000)
        val targets = ordinary.filter { it.length >= 4 }
        val lexicalPartsOfSpeech = setOf("verb", "adjective", "adverb")
        val excluded = mapOf(
            "it" to "I.T", "in" to "I.N", "do" to "d/o", "co" to "c/o", "be" to "B/E", "us" to "U/S",
            "bette" to "Better.com", "donati" to "DonationCoder.com", "privat" to "Private.Me",
            "and" to "Andrew", "august" to "Augusta", "how" to "Howard", "owe" to "Owen",
        )
        val expected = mapOf(
            "bette" to "better", "donati" to "donations", "privat" to "private", "ne" to "never",
            "peopl" to "people", "schoo" to "school",
        )
        val retained = mapOf(
            "andr" to "Andrew", "howa" to "Howard", "and" to "Android",
            "nextj" to "Next.js", "nextjs" to "Next.js",
            "opena" to "OpenAI", "chatg" to "ChatGPT", "gith" to "GitHub",
        )
        val failures = mutableListOf<String>()
        for (legacy in listOf(false, true)) {
            val path = if (legacy) "legacy" else "conversion"
            val inputsToQuery = (ordinary + partial + excluded.keys + expected.keys + retained.keys).distinct()
            val byInput = inputsToQuery.associateWith { input ->
                val candidates = if (legacy) engine.getCandidates(input, false, true)
                else engine.getConversionCandidates(input, input, false, true, true, emptyList())
                candidates.map { it.commitText }.distinctBy(String::lowercase)
            }
            fun visible(input: String) = byInput.getValue(input).take(8)
            fun report(message: String) = InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                putString("stream", "\nENGLISH_COMPLETION $path $message\n")
            })

            report("coverage distinct=${inputsToQuery.size} bigrams=${corpus.bigrams.size} " +
                "referencePrefixes=${corpus.expectedCompletions.size}")
            for ((kind, inputs) in listOf("word" to ordinary, "partial" to partial)) {
                val symbols = mutableListOf<String>()
                val names = mutableListOf<String>()
                val premature = mutableListOf<String>()
                val stylizedFirst = mutableListOf<String>()
                for (input in inputs) {
                    val candidates = visible(input)
                    val reference = corpus.expectedCompletions[input].orEmpty()
                    for ((index, surface) in candidates.withIndex()) {
                        val key = surface.lowercase()
                        if (key == input) continue
                        val example = "$input => $surface (${index + 1})"
                        val alternatives = reference.filter { word ->
                            candidates.take(index).none { it.equals(word, ignoreCase = true) }
                        }
                        val mark = surface.indexOfFirst { it.isDigit() || it in "./_+:&@#" }
                        if (mark >= 0 && surface.none(Char::isWhitespace)) {
                            symbols += example
                            if (mark >= input.length && alternatives.isNotEmpty()) {
                                premature += "$example before ${alternatives.joinToString()}"
                            }
                        }
                        val component = surface.zipWithNext().indexOfFirst { (a, b) ->
                            a.isLowerCase() && b.isUpperCase()
                        } + 1
                        if (surface.all(Char::isLetter) && component > input.length && alternatives.isNotEmpty()) {
                            stylizedFirst += "$example before ${alternatives.joinToString()}"
                        }
                        val roles = words.grammarRoles.lookup(surface, key)
                        if (roles.any { it == "noun_proper" || it.startsWith("name_") } &&
                            (surface.any(Char::isUpperCase) || roles.none { it in lexicalPartsOfSpeech })
                        ) {
                            names += example
                        }
                    }
                }
                // Raw name and symbol counts include intentional spellings. Only
                // early identifiers that outrank reference words fail this check.
                report("$kind cases=${inputs.size} symbol=${symbols.size} name=${names.size} " +
                    "premature=${premature.size} stylizedFirst=${stylizedFirst.size}")
                report("$kind premature examples=${premature.take(20)}")
                report("$kind stylized examples=${stylizedFirst.take(20)}")
                report("$kind symbol examples=${symbols.take(20)}")
                report("$kind name examples=${names.take(20)}")
                if (premature.isNotEmpty()) {
                    failures += "$path $kind: ${premature.size} early identifiers: ${premature.take(10)}"
                }
                if (stylizedFirst.isNotEmpty()) {
                    failures += "$path $kind: ${stylizedFirst.size} early stylized spellings: ${stylizedFirst.take(10)}"
                }
            }
            val misses = targets.filter { word ->
                visible(word.dropLast(1)).none { it.equals(word, ignoreCase = true) }
            }
            val hits = targets.size - misses.size
            report("penultimate recall=$hits/${targets.size} misses=${misses.take(20)}")
            if (hits * 100 < targets.size * 95) failures += "$path: penultimate recall $hits/${targets.size}"
            for (input in corpus.firstKeys) {
                val extensions = byInput.getValue(input).filterNot { it.equals(input, ignoreCase = true) }
                if (extensions.isNotEmpty()) failures += "$path $input: unexpected extensions $extensions"
            }
            for ((input, surface) in excluded) {
                val candidates = byInput.getValue(input)
                if (candidates.any { it.equals(surface, ignoreCase = true) }) {
                    failures += "$path $input: unexpected $surface in $candidates"
                }
            }
            val ne = visible("ne")
            if (!ne.containsAll(listOf("new", "need", "next"))) failures += "$path ne: missing ordinary words in $ne"
            for ((input, surface) in retained) {
                val candidates = byInput.getValue(input)
                if (surface !in candidates) failures += "$path $input: missing $surface in $candidates"
            }
            for ((input, surface) in expected) {
                val candidates = visible(input)
                if (!candidates.getOrNull(1).equals(surface, ignoreCase = true)) {
                    failures += "$path $input: expected $surface second in $candidates"
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun indexedCompletionPreservesOrderedCandidates() = withDictionary { _, _, reader ->
        val data = checkNotNull(reader.loadBundledEnglishForEngine())
        checkNotNull(data.wordCompletionBounds)
        checkNotNull(data.phraseCompletionBounds)
        val grammar = checkNotNull(reader.loadBundledEnglishGrammar())
        val unindexed = data.copy(wordCompletionBounds = null, phraseCompletionBounds = null)
        val indexedConverter = PhraseConverter(EnglishPhraseDictionary(data, grammar), beamWidth = 24, completionLimit = 32)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val verifiedGrammar = context.assets.open("english/grammar/categories.erol").use {
            RuntimeGrammarRoles.read(ByteBuffer.wrap(it.readBytes()))
        }
        val referenceConverter = PhraseConverter(EnglishPhraseDictionary(unindexed, verifiedGrammar), beamWidth = 24, completionLimit = 32)
        val inputs = listOf(
            "he", "th", "an", "in", "re", "op", "te", "ke", "zz", "re-", "don'", "a1",
            "ne", "nex", "juris", "ch", "gi", "gith",
            "the", "there", "keyboa", "hecantake", "he can tak", "New Yo", "elonmusk",
            "taylor swift", "twofactorauthentication", "thequickbrownfoxjumpsoverthelazydog",
        )
        for (text in inputs) for (prefix in listOf(false, true)) {
            assertEquals("$text prefix=$prefix", referenceConverter.convert(text, prefix, 12), indexedConverter.convert(text, prefix, 12))
        }
    }

    @Test
    fun sampledVocabularyPrefixesPreserveTypedWordBoundaries() = withDictionary { _, _, reader ->
        val data = checkNotNull(reader.loadBundledEnglishForEngine())
        val words = EnglishPhraseDictionary(data, checkNotNull(reader.loadBundledEnglishGrammar()))
        val converter = PhraseConverter(words, beamWidth = 24, completionLimit = 32)
        val samples = EnglishBoundaryCorpus.sample(data, words)
        var cases = 0
        val failures = mutableListOf<String>()
        for (sample in samples) {
            val compact = sample.reading.replace(" ", "")
            var rawEnd = 0
            for (length in 1..compact.length) {
                while (sample.reading[rawEnd++] == ' ') { /* Skip stored word boundaries. */ }
                val expectedPrefix = sample.reading.take(rawEnd)
                assertTrue(expectedPrefix, data.readingTrie.prefixesMatchingJoinedInput(
                    compact.take(length), data.readingLbsIndex,
                ).contains(expectedPrefix))
            }
            val inputs = ((2..compact.length).map(compact::take) +
                listOf(compact.uppercase(), compact.replaceFirstChar(Char::uppercaseChar))).distinct()
            for (word in inputs) for (context in listOf("", "hello ")) for (prefix in listOf(false, true)) {
                val input = context + word
                val bad = converter.convert(input, prefix, 12).filter { candidate ->
                    candidate.segments.any { it.inferredSpace && it.start >= context.length }
                }
                if (bad.isNotEmpty() && failures.size < 20) failures +=
                    "${sample.bucket}: $input prefix=$prefix => ${bad.map { it.text }}"
                cases++
            }
        }
        android.util.Log.i("WordBoundaryHarness", "samples=${samples.size} cases=$cases failures=${failures.size}")
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun joinedSentencesKeepTheirWordsAndUnknownTextDoesNotBecomeAcronymChains() = withDictionary { _, _, reader ->
        val words = EnglishPhraseDictionary(checkNotNull(reader.loadBundledEnglishForEngine()),
            checkNotNull(reader.loadBundledEnglishGrammar()))
        val converter = PhraseConverter(words, beamWidth = 24, completionLimit = 32)
        for (sentence in listOf("he can take", "we are working", "the cat is sleeping", "cats are cute",
            "I love you", "I am happy", "they can read", "we will come", "she is ready")) {
            val input = sentence.replace(" ", "").lowercase()
            var position = 0
            val allowed = sentence.split(' ').dropLast(1).mapTo(hashSetOf()) { word ->
                position += word.length
                position
            }
            for (prefix in listOf(false, true)) {
                val candidates = converter.convert(input, prefix, 12)
                assertTrue("$input prefix=$prefix: ${candidates.map { it.text }}",
                    candidates.any { it.text.equals(sentence, ignoreCase = true) })
                assertTrue("$input prefix=$prefix: ${candidates.map { it.text }}", candidates.all { candidate ->
                    candidate.segments.none { it.inferredSpace && it.start !in allowed }
                })
            }
        }
        val random = java.util.Random(76021)
        repeat(40) {
            val input = (0 until 10).map { "qxz"[random.nextInt(3)] }.joinToString("")
            for (prefix in listOf(false, true)) assertTrue(input,
                converter.convert(input, prefix, 12).all { candidate -> candidate.segments.none { it.inferredSpace } })
        }
    }

    @Test
    fun everyNamePrefixAvoidsInventedWordBoundariesInBothModes() = withDictionary { _, _, reader ->
        val words = EnglishPhraseDictionary(checkNotNull(reader.loadBundledEnglishForEngine()),
            checkNotNull(reader.loadBundledEnglishGrammar()))
        val converter = PhraseConverter(words, beamWidth = 24, completionLimit = 32)
        val engine = EnglishEngine().apply { configureLazyDictionaryLoading(reader) }
        var cases = 0
        for (name in listOf("Elon Musk", "Taylor Swift", "Bill Gates", "Steve Jobs",
            "Mark Zuckerberg", "Sundar Pichai", "Satya Nadella")) {
            val joined = name.lowercase().replace(" ", "")
            for (length in 2..joined.length) for (prefixMode in listOf(false, true)) {
                val input = joined.take(length)
                val phrases = converter.convert(input, prefixMode, 12)
                val invented = phrases.filter { phrase ->
                    phrase.segments.any { it.inferredSpace } && !phrase.text.equals(name, ignoreCase = true)
                }
                assertTrue("$input prefix=$prefixMode: ${invented.map { it.text }}", invented.isEmpty())
                val candidates = engine.getConversionCandidates(input, input, false, true, prefixMode, emptyList())
                assertFalse("$input prefix=$prefixMode", candidates.any {
                    it.string in listOf("elon mu", "Elon mu", "el on mu", "El on mu")
                })
                if (length == joined.length || prefixMode && input == "elonmu") {
                    assertTrue("$input prefix=$prefixMode: ${candidates.map { it.string }}",
                        candidates.any { it.commitText == name })
                }
                cases++
            }
        }
        android.util.Log.i("NamePrefixHarness", "Passed $cases name-prefix/mode cases")
    }

    @Test
    fun previewThreeBundlesTheExpandedVocabularyAndFrontCodedGrammar() = withDictionary { _, _, reader ->
        val dictionary = checkNotNull(reader.loadBundledEnglishForEngine())
        assertEquals(864_023, dictionary.tokenArray.getNodeIds().size)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val header = ByteArray(8)
        context.assets.open("english/grammar/categories.erol").use { DataInputStream(it).readFully(header) }
        assertEquals(2, ByteBuffer.wrap(header).getInt(4))
        val words = EnglishPhraseDictionary(dictionary, checkNotNull(reader.loadBundledEnglishGrammar()))
        for (surface in listOf("Elon Musk", "Taylor Swift", "Toyota Corolla", "OpenAI")) {
            assertTrue(surface, words.exact(surface.lowercase()).any { it.surface == surface })
        }
    }

    @Test
    fun registeredMultiwordSurfacesKeepSpacesInCandidateCommitText() = withDictionary { _, _, reader ->
        val words = EnglishPhraseDictionary(
            checkNotNull(reader.loadBundledEnglishForEngine()),
            checkNotNull(reader.loadBundledEnglishGrammar()),
        )
        val engine = EnglishEngine().apply { configureLazyDictionaryLoading(reader) }
        fun query(input: String, prediction: Boolean = true, prefixMode: Boolean = true) =
            engine.getConversionCandidates(input, input, false, prediction, prefixMode, emptyList())
        for ((input, expected) in listOf(
            "android" to "Android Studio", "androidstudio" to "Android Studio",
            "android s" to "Android Studio", "googlemaps" to "Google Maps",
        )) {
            val phrases = words.phrases(input, prefixMode = true, limit = 12)
            assertTrue(input, phrases.any { it.surface == expected })
            assertTrue(input, phrases.all { it in words.exact(it.reading) })
            val candidates = query(input)
            val canonical = candidates.first { it.string == expected }
            if (input != "android") {
                assertTrue("$input: ${candidates.map { it.string }}", candidates.indexOf(canonical) in 1..11)
            }
            assertEquals(expected, canonical.commitText)
            assertEquals(input.length, canonical.length.toInt())
        }
        val android = query("android").map { it.string }
        val identifierIndex = android.indexOf("AndroidStudio")
        assertTrue(identifierIndex < 0 || android.indexOf("Android Studio") < identifierIndex)
        assertTrue(query("AndroidStudio").any { it.commitText == "AndroidStudio" })
        assertFalse(query("googlemaps").any { it.string.equals("Google ma ps", ignoreCase = true) })
        assertTrue(query("TextView").any { it.commitText == "TextView" })
        assertFalse(query("android", prediction = false).any { it.string == "Android Studio" })
        assertFalse(query("android", prefixMode = false).any { it.string == "Android Studio" })
        assertTrue(query("androidstudio", prefixMode = false).any { it.string == "Android Studio" })
    }

    @Test
    fun barePrefixesRankWordsBeforeUnfinishedRegisteredPhrases() = withDictionary { _, _, reader ->
        val engine = EnglishEngine().apply { configureLazyDictionaryLoading(reader) }
        fun query(input: String, prefixMode: Boolean = true): List<String> =
            engine.getConversionCandidates(
                input, input.takeIf { it.none(Char::isWhitespace) }, false, true, prefixMode, emptyList(),
            )
                .map { it.commitText }

        for ((input, word) in listOf(
            "ja" to "Java", "ba" to "battery", "ra" to "ran", "ta" to "tap", "op" to "opens", "auto" to "AutoCAD",
        )) {
            val candidates = query(input)
            assertEquals("$input: $candidates", listOf(input, word), candidates.take(2))
        }

        val january = query("jan")
        assertTrue(january.toString(), "January" in january.take(4))

        val auto = query("auto")
        val automatic = auto.indexOf("automatic")
        assertTrue(auto.toString(), automatic in 1..5)
        assertTrue(auto.toString(), auto.indexOf("Auto 5000") > automatic)
        assertTrue(auto.toString(), auto.indexOf("auto loan") > automatic)

        val sparsePrefix = query("zx")
        val wordIndex = sparsePrefix.indexOf("ZXPInstaller")
        assertTrue(sparsePrefix.toString(), wordIndex >= 0 && sparsePrefix.indexOf("ZX Auto") > wordIndex)

        assertEquals("J and C Carmichael", query("j a").getOrNull(1))
        assertEquals("auto loan", query("auto l").getOrNull(1))
        for (prefixMode in listOf(false, true)) {
            assertTrue("auto loan" in query("autoloan", prefixMode))
            assertTrue("MG F" in query("mgf", prefixMode))
            assertTrue("J and C Carmichael" in query("jandccarmichael", prefixMode))
        }
    }

    @Test
    fun wordBoundariesNeedTypedOrLexicalEvidence() = withDictionary { _, _, reader ->
        val engine = EnglishEngine().apply { configureLazyDictionaryLoading(reader) }
        fun query(input: String, prefix: Boolean) =
            engine.getConversionCandidates(input, input, false, true, prefix, emptyList()).map { it.commitText }

        for (prefix in listOf(true, false)) {
            for (input in listOf("a", "an", "he", "in", "the", "androi")) {
                val candidates = query(input, prefix)
                assertTrue("$input: $candidates", input in candidates)
                assertFalse("$input: $candidates", candidates.any { it.any(Char::isWhitespace) })
            }
            for ((input, compound) in listOf("curryrice" to "curry rice", "chickenrice" to "chicken rice")) {
                val candidates = query(input, prefix)
                assertEquals("$input: $candidates", input, candidates.first())
                assertEquals("$input: $candidates", compound, candidates.firstOrNull { it.any(Char::isWhitespace) })
            }
            assertTrue(query("anqi", prefix).contains("An Qi"))
            assertTrue(query("androidstudio", prefix).contains("Android Studio"))
        }
        assertTrue(query("an", true).contains("and"))
        assertTrue(query("android", true).contains("Android Studio"))
        assertTrue(query("an q", true).contains("An Qi"))
        assertTrue(query("hecantake", true).contains("he can take"))
    }

    @Test
    fun registeredWordsAndDirectPrefixesDoNotInventWordBoundaries() = withDictionary { _, _, reader ->
        val dictionary = EnglishPhraseDictionary(
            checkNotNull(reader.loadBundledEnglishForEngine()),
            checkNotNull(reader.loadBundledEnglishGrammar()),
        )
        val converter = PhraseConverter(dictionary, beamWidth = 24, completionLimit = 32)
        for (input in listOf("android", "hello", "keyboard", "important", "morning", "therapist")) {
            assertTrue(input, dictionary.exact(input).isNotEmpty())
            for (prefixMode in listOf(false, true)) {
                val candidates = converter.convert(input, prefixMode, limit = 12)
                assertTrue(input, candidates.isNotEmpty())
                assertFalse(input, candidates.any { phrase -> phrase.segments.any { it.inferredSpace } })
            }
        }
        val prefix = converter.convert("keybo", prefixMode = true, limit = 12)
        assertTrue(prefix.any { it.text == "keyboard" })
        assertFalse(prefix.any { phrase -> phrase.segments.any { it.inferredSpace } })

        val joined = converter.convert("hecantak", prefixMode = true, limit = 12)
        assertTrue(joined.any { it.text == "he can take" })
        assertTrue(converter.convert("hecantake", prefixMode = false, limit = 12).any { it.text == "he can take" })
        assertTrue(converter.convert("goodmorning", prefixMode = true, limit = 12).any { it.text == "good morning" })
        assertTrue(converter.convert("twofactorauthentication", prefixMode = true, limit = 12)
            .any { it.text == "two-factor authentication" })
        assertTrue(converter.convert("twofactorauthentication", prefixMode = true, limit = 12)
            .any { it.text == "two factor authentication" })
        assertEquals("he takes", converter.convert("he tak", prefixMode = true, limit = 12).first().text)
    }

    @Test
    fun packagedDictionaryKeepsGrammarAndGlideExamples() = withDictionary { _, resolver, reader ->
        val dictionary = checkNotNull(reader.loadBundledEnglishForEngine())
        val grammar = checkNotNull(reader.loadBundledEnglishGrammar())
        val converter = PhraseConverter(EnglishPhraseDictionary(dictionary, grammar))
        val joined = converter.convert("hecantake", prefixMode = false, limit = 12).first()
        assertEquals("he can take", joined.text)
        assertEquals(listOf(0 to 2, 2 to 5, 5 to 9), joined.segments.map { it.start to it.end })
        assertEquals(4669, joined.dictionaryCost)
        val completion = converter.convert("he tak", prefixMode = true, limit = 5)
        assertEquals(listOf("he takes", "He takes", "he taking", "he taken", "he takeover"), completion.map { it.text })
        assertEquals(3, completion.first().grammar.score)
        assertEquals(1, completion.first().grammar.clauses)
        assertEquals(listOf(0 to 2, 2 to 3, 3 to 6), completion.first().segments.map { it.start to it.end })
        assertEquals("don't", converter.convert("don’", prefixMode = true, limit = 12).first().text)
        assertTrue(converter.convert("google maps", prefixMode = false, limit = 12).any { it.text == "Google Maps" })
        assertTrue(QwertyGlidePrebuiltDictionaryLoader(resolver).load() is QwertyGlidePrebuiltLoadResult.Loaded)
    }

    @Test
    fun predictionAndDictionarySwitchesControlPhraseCandidates() = withDictionary { store, resolver, reader ->
        val engine = EnglishEngine().apply { configureLazyDictionaryLoading(reader) }
        fun query(enabled: Boolean) =
            engine.getConversionCandidates("hecantake", "hecantake", false, enabled, true, emptyList())

        assertFalse(query(false).any { it.string == "he can take" })
        val enabled = query(true)
        assertEquals("hecantake", enabled.first().string)
        assertEquals(9, enabled.first { it.string == "he can take" }.length.toInt())
        for ((input, expected) in listOf("he tak" to "he takes", "don’" to "don't", "they tak" to "they take")) {
            val suggestions = engine.getConversionCandidates(input, null, false, true, true, emptyList())
            assertEquals(input, expected, suggestions.getOrNull(1)?.string)
        }

        val specs = DictionaryFileSpecs.forCategory(DictionaryCategory.ENGLISH)
        for (spec in specs) {
            resolver.openBundledForKey(spec.key).use {
                assertTrue(store.saveOverrideFromInputStream(spec.key, it).isValid)
            }
        }
        engine.reloadDictionariesFromCurrentSources(reader)
        assertFalse(query(true).any { it.string == "he can take" })
        assertTrue(QwertyGlidePrebuiltDictionaryLoader(resolver).load() is QwertyGlidePrebuiltLoadResult.NotAvailable)

        store.setExternalEnabledForCategory(DictionaryCategory.ENGLISH, false)
        engine.reloadDictionariesFromCurrentSources(reader)
        assertTrue(query(true).any { it.string == "he can take" })
    }

    @Test
    fun conversionTabShowsExactPhrasesWithoutCompletions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entryPoint = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
        val engine = entryPoint.kanaKanjiEngine()
        val repository = entryPoint.userDictionaryRepository()
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(engine, backend)
            for (bunsetsu in listOf(false, true)) {
                suspend fun query(input: String, englishPrediction: Boolean = true) = session.query(
                    KanaKanjiQueryRequest(
                        input = input,
                        mode = CandidateQueryMode.CONVERSION,
                        bunsetsuSeparation = bunsetsu,
                        n = 4,
                        userDictionaryRepository = repository,
                        learnRepository = null,
                        omissionSearchEnabled = false,
                        typoCorrectionJapaneseFlickEnabled = false,
                        typoCorrectionQwertyEnglishEnabled = false,
                        typoCorrectionOffsetScore = 3000,
                        omissionSearchOffsetScore = 1900,
                        beamWidth = 20,
                        predictionConfig = PredictionConfig(englishPredictionEnabled = englishPrediction),
                    ),
                ).candidates

                assertTrue(query("hecantake").any { it.string == "he can take" && it.length.toInt() == 9 })
                for (unfinished in listOf("elonm", "elonmu")) {
                    val candidates = query(unfinished)
                    assertFalse("$backend bunsetsu=$bunsetsu $unfinished: ${candidates.map { it.string }}",
                        candidates.any { it.string.any(Char::isWhitespace) })
                }
                assertFalse(query("he tak").any { it.string == "he takes" })
                assertFalse(query("hecantake", englishPrediction = false).any { it.string == "he can take" })
            }
        }
    }

    @Test
    fun contextualPrefixesUseRegisteredSpacesAcrossBothBackends() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
        val reader = entry.dictionaryBinaryReader()
        val words = EnglishPhraseDictionary(checkNotNull(reader.loadBundledEnglishForEngine()),
            checkNotNull(reader.loadBundledEnglishGrammar()))
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(entry.kanaKanjiEngine(), backend)
            for (bunsetsu in listOf(false, true)) for (mode in listOf(
                CandidateQueryMode.NO_TAB_DEFAULT, CandidateQueryMode.CONVERSION,
            )) for (input in listOf("hello froste", "hello noki", "hello sourceseri", "hello bolind", "hello chanelim")) {
                val candidates = session.query(KanaKanjiQueryRequest(
                    input = input, mode = mode, bunsetsuSeparation = bunsetsu, n = 4,
                    userDictionaryRepository = entry.userDictionaryRepository(), learnRepository = null,
                    omissionSearchEnabled = false, typoCorrectionJapaneseFlickEnabled = false,
                    typoCorrectionQwertyEnglishEnabled = false, typoCorrectionOffsetScore = 3000,
                    omissionSearchOffsetScore = 1900, beamWidth = 20,
                    predictionConfig = PredictionConfig(englishPredictionEnabled = true),
                )).candidates
                assertTrue(input, candidates.isNotEmpty())
                for (candidate in candidates) {
                    val suffix = candidate.string.substringAfter(' ', "")
                    if (suffix.none(Char::isWhitespace)) continue
                    val keys = listOf(suffix.lowercase(), suffix.lowercase().filterNot(Char::isWhitespace))
                    assertTrue("$backend $mode bunsetsu=$bunsetsu $input => ${candidate.string}",
                        keys.any { key -> words.exact(key).any { it.surface.equals(suffix, ignoreCase = true) } })
                }
            }
        }
    }

    @Test
    fun corruptGrammarIndexIsRejected() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bytes = context.assets.open("english/grammar/categories.erol").use { it.readBytes() }
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) { RuntimeGrammarRoles.read(ByteBuffer.wrap(bytes)) }
    }

    private fun withDictionary(
        test: (DictionaryOverrideStore, DictionarySourceResolver, DictionaryBinaryReader) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "english-phrase-test-${UUID.randomUUID()}"
        val directory = File(context.cacheDir, name)
        val overridePrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val defaultPrefs = context.getSharedPreferences("$name-default", Context.MODE_PRIVATE)
        val store = DictionaryOverrideStore(directory, overridePrefs, defaultPrefs)
        val resolver = DictionarySourceResolver(context, store)
        try {
            test(store, resolver, DictionaryBinaryReader(resolver, store))
        } finally {
            directory.deleteRecursively()
            context.deleteSharedPreferences(name)
            context.deleteSharedPreferences("$name-default")
        }
    }
}
