package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.os.Debug
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.session.CandidateQueryMode
import com.kazumaproject.markdownhelperkeyboard.converter.session.ConversionBackend
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiConversionSession
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiQueryRequest
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.SystemNgramRuntime
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileKey
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileSpecs
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.reflect.Modifier
import java.util.BitSet
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.ceil
import kotlin.system.measureNanoTime

@RunWith(AndroidJUnit4::class)
class IncrementalConversionSessionPerformanceInstrumentedTest {

    @Test
    fun compactCoreDictionariesMatchSerializedAssetsOnDevice() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("compactDictionaryParityProbe") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java,
        )
        val reader = entryPoint.dictionaryBinaryReader()
        val triples = listOf(
            Triple(DictionaryFileKey.SYSTEM_TANGO, DictionaryFileKey.SYSTEM_YOMI, DictionaryFileKey.SYSTEM_TOKEN),
            Triple(DictionaryFileKey.SINGLE_KANJI_TANGO, DictionaryFileKey.SINGLE_KANJI_YOMI, DictionaryFileKey.SINGLE_KANJI_TOKEN),
            Triple(DictionaryFileKey.EMOJI_TANGO, DictionaryFileKey.EMOJI_YOMI, DictionaryFileKey.EMOJI_TOKEN),
            Triple(DictionaryFileKey.EMOTICON_TANGO, DictionaryFileKey.EMOTICON_YOMI, DictionaryFileKey.EMOTICON_TOKEN),
            Triple(DictionaryFileKey.SYMBOL_TANGO, DictionaryFileKey.SYMBOL_YOMI, DictionaryFileKey.SYMBOL_TOKEN),
            Triple(DictionaryFileKey.READING_CORRECTION_TANGO, DictionaryFileKey.READING_CORRECTION_YOMI, DictionaryFileKey.READING_CORRECTION_TOKEN),
            Triple(DictionaryFileKey.KOTOWAZA_TANGO, DictionaryFileKey.KOTOWAZA_YOMI, DictionaryFileKey.KOTOWAZA_TOKEN),
        )
        val report = buildString {
            appendLine("device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} API ${android.os.Build.VERSION.SDK_INT}")
            triples.forEach { (tangoKey, yomiKey, tokenKey) ->
                val originalTango = context.assets.open(DictionaryFileSpecs.get(tangoKey).bundledAssetPath).let { input ->
                    LOUDS().readExternalNotCompress(reader.openZipAwareObjectInputStream(input, tangoKey.name))
                }
                val originalYomi = context.assets.open(DictionaryFileSpecs.get(yomiKey).bundledAssetPath).let { input ->
                    LOUDSWithTermId().readExternalNotCompress(reader.openZipAwareObjectInputStream(input, yomiKey.name))
                }
                val originalToken = context.assets.open(DictionaryFileSpecs.get(tokenKey).bundledAssetPath).let { input ->
                    TokenArray().also { tokenArray ->
                        tokenArray.readExternal(reader.openZipAwareObjectInputStream(input, tokenKey.name))
                    }
                }.also(reader::readPosTableInto)
                val compactTango = reader.loadLouds(tangoKey)
                val compactYomi = reader.loadLoudsWithTermId(yomiKey)
                val compactToken = reader.loadTokenArray(tokenKey)
                val category = DictionaryFileSpecs.get(tangoKey).category

                assertEquals("$category tango LBS", originalTango.LBS, compactTango.LBS)
                assertEquals("$category tango leaves", originalTango.isLeaf, compactTango.isLeaf)
                assertTrue("$category tango labels", originalTango.getAllLabels().contentEquals(compactTango.getAllLabels()))
                assertEquals("$category yomi LBS", originalYomi.LBS, compactYomi.LBS)
                assertEquals("$category yomi leaves", originalYomi.isLeaf, compactYomi.isLeaf)
                assertTrue("$category yomi labels", originalYomi.getAllLabels().contentEquals(compactYomi.getAllLabels()))
                assertTrue("$category term ids", originalYomi.getAllTermIds().contentEquals(compactYomi.getAllTermIds()))
                assertTrue("$category POS indices", originalToken.getPosTableIndices().contentEquals(compactToken.getPosTableIndices()))
                assertTrue("$category word costs", originalToken.getWordCosts().contentEquals(compactToken.getWordCosts()))
                assertTrue("$category node ids", originalToken.getNodeIds().contentEquals(compactToken.getNodeIds()))
                assertEquals("$category token bits", originalToken.bitvector, compactToken.bitvector)
                assertTrue("$category left ids", originalToken.leftIds.contentEquals(compactToken.leftIds))
                assertTrue("$category right ids", originalToken.rightIds.contentEquals(compactToken.rightIds))
                appendLine(
                    "$category=exact," +
                        "tangoLabels=${compactTango.getAllLabels().size}," +
                        "yomiLabels=${compactYomi.getAllLabels().size}," +
                        "tokens=${compactToken.getNodeIds().size}",
                )
            }
            val originalEnglishReading = context.assets.open(
                DictionaryFileSpecs.get(DictionaryFileKey.ENGLISH_READING).bundledAssetPath,
            ).let { input ->
                com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId()
                    .readExternalNotCompress(
                        reader.openZipAwareObjectInputStream(input, DictionaryFileKey.ENGLISH_READING.name),
                    )
            }
            val originalEnglishWord = context.assets.open(
                DictionaryFileSpecs.get(DictionaryFileKey.ENGLISH_WORD).bundledAssetPath,
            ).let { input ->
                com.kazumaproject.markdownhelperkeyboard.converter.english.louds.LOUDS()
                    .readExternalNotCompress(
                        reader.openZipAwareObjectInputStream(input, DictionaryFileKey.ENGLISH_WORD.name),
                    )
            }
            val originalEnglishToken = context.assets.open(
                DictionaryFileSpecs.get(DictionaryFileKey.ENGLISH_TOKEN).bundledAssetPath,
            ).let { input ->
                com.kazumaproject.markdownhelperkeyboard.converter.english.tokenArray.TokenArray()
                    .readExternal(
                        reader.openZipAwareObjectInputStream(input, DictionaryFileKey.ENGLISH_TOKEN.name),
                    )
            }
            val compactEnglishReading = reader.loadEnglishReading(DictionaryFileKey.ENGLISH_READING)
            val compactEnglishWord = reader.loadEnglishWord(DictionaryFileKey.ENGLISH_WORD)
            val compactEnglishToken = reader.loadEnglishToken(DictionaryFileKey.ENGLISH_TOKEN)
            assertEquals("ENGLISH reading LBS", originalEnglishReading.LBS, compactEnglishReading.LBS)
            assertEquals("ENGLISH reading leaves", originalEnglishReading.isLeaf, compactEnglishReading.isLeaf)
            assertTrue("ENGLISH reading labels", originalEnglishReading.labels.contentEquals(compactEnglishReading.labels))
            assertTrue("ENGLISH term ids", originalEnglishReading.termIdsSaved.contentEquals(compactEnglishReading.termIdsSaved))
            assertEquals("ENGLISH word LBS", originalEnglishWord.LBS, compactEnglishWord.LBS)
            assertEquals("ENGLISH word leaves", originalEnglishWord.isLeaf, compactEnglishWord.isLeaf)
            assertTrue("ENGLISH word labels", originalEnglishWord.labels.contentEquals(compactEnglishWord.labels))
            assertTrue("ENGLISH word costs", originalEnglishToken.getWordCosts().contentEquals(compactEnglishToken.getWordCosts()))
            assertTrue("ENGLISH node ids", originalEnglishToken.getNodeIds().contentEquals(compactEnglishToken.getNodeIds()))
            assertEquals("ENGLISH token bits", originalEnglishToken.bitvector, compactEnglishToken.bitvector)
            appendLine(
                "ENGLISH=exact,readingLabels=${compactEnglishReading.labels.size}," +
                    "wordLabels=${compactEnglishWord.labels.size}," +
                    "tokens=${compactEnglishToken.getNodeIds().size}",
            )
            appendLine("allDictionaryFieldsExact=true")
        }
        File(context.filesDir, "conversion-perf").apply { mkdirs() }
            .resolve("compact-dictionary-parity.txt")
            .writeText(report)
        println(report)
    }

    @Test
    fun measureDictionaryColdLoadBreakdownOnDevice() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("dictionaryColdLoadProbe") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java,
        )
        val reader = entryPoint.dictionaryBinaryReader()
        val retained = ArrayList<Any>(64)
        val phases = linkedMapOf<String, Double>()

        fun measure(name: String, block: () -> Unit) {
            phases[name] = measureNanoTime(block) / 1_000_000.0
        }

        fun loadTriple(
            name: String,
            tangoKey: DictionaryFileKey,
            yomiKey: DictionaryFileKey,
            tokenKey: DictionaryFileKey,
        ) {
            lateinit var tango: com.kazumaproject.Louds.LOUDS
            lateinit var yomi: com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
            lateinit var token: com.kazumaproject.dictionary.TokenArray
            measure("$name.data") {
                tango = reader.loadLouds(tangoKey)
                yomi = reader.loadLoudsWithTermId(yomiKey)
                token = reader.loadTokenArray(tokenKey)
                retained += tango
                retained += yomi
                retained += token
            }
            measure("$name.indexes") {
                retained += reader.loadYomiLbsIndex(yomiKey, yomi)
                retained += reader.loadYomiLeafIndex(yomiKey, yomi)
                retained += reader.loadTokenIndex(tokenKey, token)
                retained += reader.loadTangoLbsIndex(tangoKey, tango)
            }
        }

        forceGc()
        measure("connection") { retained += reader.loadConnectionMatrix() }
        loadTriple(
            "system",
            DictionaryFileKey.SYSTEM_TANGO,
            DictionaryFileKey.SYSTEM_YOMI,
            DictionaryFileKey.SYSTEM_TOKEN,
        )
        loadTriple(
            "singleKanji",
            DictionaryFileKey.SINGLE_KANJI_TANGO,
            DictionaryFileKey.SINGLE_KANJI_YOMI,
            DictionaryFileKey.SINGLE_KANJI_TOKEN,
        )
        loadTriple(
            "emoji",
            DictionaryFileKey.EMOJI_TANGO,
            DictionaryFileKey.EMOJI_YOMI,
            DictionaryFileKey.EMOJI_TOKEN,
        )
        loadTriple(
            "emoticon",
            DictionaryFileKey.EMOTICON_TANGO,
            DictionaryFileKey.EMOTICON_YOMI,
            DictionaryFileKey.EMOTICON_TOKEN,
        )
        loadTriple(
            "symbol",
            DictionaryFileKey.SYMBOL_TANGO,
            DictionaryFileKey.SYMBOL_YOMI,
            DictionaryFileKey.SYMBOL_TOKEN,
        )
        loadTriple(
            "readingCorrection",
            DictionaryFileKey.READING_CORRECTION_TANGO,
            DictionaryFileKey.READING_CORRECTION_YOMI,
            DictionaryFileKey.READING_CORRECTION_TOKEN,
        )
        loadTriple(
            "kotowaza",
            DictionaryFileKey.KOTOWAZA_TANGO,
            DictionaryFileKey.KOTOWAZA_YOMI,
            DictionaryFileKey.KOTOWAZA_TOKEN,
        )
        val report = buildString {
            appendLine("device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} API ${android.os.Build.VERSION.SDK_INT}")
            phases.forEach { (name, milliseconds) -> appendLine("$name=$milliseconds") }
            appendLine("totalMs=${phases.values.sum()}")
        }
        File(context.filesDir, "conversion-perf").apply { mkdirs() }
            .resolve("dictionary-cold-load-breakdown.txt")
            .writeText(report)
        println(report)
        assertTrue(retained.isNotEmpty())
    }

    @Test
    fun measureColdStartConversionOnDevice() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("conversionColdStartProbe") == "true")
        val backend = arguments.getString("conversionColdStartBackend")
            ?.let(ConversionBackend::valueOf)
            ?: ConversionBackend.INCREMENTAL_SESSION
        val firstInputKind = arguments.getString("conversionColdStartFirstInput")
            ?: "JAPANESE"
        val firstInput = when (firstInputKind) {
            "ENGLISH" -> "andr"
            else -> "このあぷりabc123へんかん"
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        forceGc()
        lateinit var entryPoint: KanaKanjiEngineEntryPoint
        val entryPointNs = measureNanoTime {
            entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                KanaKanjiEngineEntryPoint::class.java,
            )
        }
        lateinit var engine: KanaKanjiEngine
        val engineInitializationNs = measureNanoTime {
            engine = entryPoint.kanaKanjiEngine()
        }
        val repository = entryPoint.userDictionaryRepository()

        suspend fun queryOnce(input: String): Pair<Long, List<Candidate>> {
            lateinit var candidates: List<Candidate>
            val elapsedNs = measureNanoTime {
                candidates = KanaKanjiConversionSession(engine, backend).query(
                    request(
                        input = input,
                        repository = repository,
                        mode = CandidateQueryMode.PREDICTION,
                        bunsetsuSeparation = true,
                    ),
                ).candidates
            }
            return elapsedNs to candidates
        }

        val (firstQueryNs, firstCandidates) = queryOnce(firstInput)
        val (secondQueryNs, secondCandidates) = queryOnce(firstInput)
        val report = buildString {
            appendLine("device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} API ${android.os.Build.VERSION.SDK_INT}")
            appendLine("backend=$backend")
            appendLine("firstInputKind=$firstInputKind")
            appendLine("firstInput=$firstInput")
            appendLine("entryPointMs=${entryPointNs / 1_000_000.0}")
            appendLine("engineInitializationMs=${engineInitializationNs / 1_000_000.0}")
            appendLine("firstQueryMs=${firstQueryNs / 1_000_000.0}")
            appendLine("secondQueryMs=${secondQueryNs / 1_000_000.0}")
            appendLine("engineAndFirstQueryMs=${(engineInitializationNs + firstQueryNs) / 1_000_000.0}")
            appendLine("firstCandidate=${firstCandidates.firstOrNull()?.string.orEmpty()}")
            appendLine("candidateParity=${firstCandidates.take(20).map { it.fingerprint() } == secondCandidates.take(20).map { it.fingerprint() }}")
        }
        File(context.filesDir, "conversion-perf").apply { mkdirs() }
            .resolve("cold-start-${backend.name.lowercase()}-${firstInputKind.lowercase()}.txt")
            .writeText(report)
        println(report)
        assertTrue(firstCandidates.isNotEmpty())
        assertEquals(
            firstCandidates.take(20).map { it.fingerprint() },
            secondCandidates.take(20).map { it.fingerprint() },
        )
    }

    @Test
    fun measureEngineRetainedPayloadOnDevice() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("conversionRetainedPayloadProbe") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java,
        )
        val engine = entryPoint.kanaKanjiEngine()
        val fields = engine.javaClass.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .associateBy { it.name }
        val groups = linkedMapOf(
            "connectionMatrix" to fields.keys.filter { it == "connectionMatrix" },
            "systemDictionary" to fields.keys.filter {
                it.startsWith("system") && !it.startsWith("systemUser")
            },
            "singleKanjiDictionary" to fields.keys.filter { it.startsWith("singleKanji") },
            "emojiDictionary" to fields.keys.filter { it.startsWith("emoji") },
            "emoticonDictionary" to fields.keys.filter { it.startsWith("emoticon") },
            "symbolDictionary" to fields.keys.filter { it.startsWith("symbol") },
            "readingCorrectionDictionary" to fields.keys.filter { it.startsWith("readingCorrection") },
            "kotowazaDictionary" to fields.keys.filter { it.startsWith("kotowaza") },
            "englishDictionary" to fields.keys.filter { it == "englishEngine" },
            "mozcBoundaryTables" to fields.keys.filter { it.startsWith("mozc") },
            "graphAndPath" to fields.keys.filter { it == "graphBuilder" || it == "findPath" },
            "readerAndOther" to fields.keys.filter { name ->
                groupsCoveredField(name).not()
            },
        )
        val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        val sizes = groups.mapValues { (_, names) ->
            names.sumOf { name ->
                val field = fields.getValue(name).apply { isAccessible = true }
                retainedPayloadBytes(field.get(engine), visited)
            }
        }
        val report = buildString {
            appendLine("ENGINE_RETAINED_PAYLOAD")
            sizes.forEach { (name, bytes) -> appendLine("$name=$bytes") }
            fields.keys.filter { it.startsWith("system") && !it.startsWith("systemUser") }
                .forEach { name ->
                    val field = fields.getValue(name).apply { isAccessible = true }
                    val isolatedVisited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
                    val fieldValue = field.get(engine)
                    appendLine("field.$name=${retainedPayloadBytes(fieldValue, isolatedVisited)}")
                    if (name in setOf("systemTangoTrie", "systemYomiTrie", "systemTokenArray")) {
                        fieldValue.javaClass.declaredFields
                            .filterNot { Modifier.isStatic(it.modifiers) || it.type.isPrimitive }
                            .forEach { nested ->
                                nested.isAccessible = true
                                val nestedVisited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
                                appendLine(
                                    "field.$name.${nested.name}=" +
                                        retainedPayloadBytes(nested.get(fieldValue), nestedVisited),
                                )
                                when (val nestedValue = nested.get(fieldValue)) {
                                    is CharArray -> appendLine(
                                        "field.$name.${nested.name}.stats=" +
                                            "length=${nestedValue.size},unique=${nestedValue.toSet().size}," +
                                            "max=${nestedValue.maxOfOrNull(Char::code) ?: 0}",
                                    )
                                    is IntArray -> appendLine(
                                        "field.$name.${nested.name}.stats=" +
                                            "length=${nestedValue.size},min=${nestedValue.minOrNull() ?: 0}," +
                                            "max=${nestedValue.maxOrNull() ?: 0}",
                                    )
                                }
                            }
                    }
                }
            appendLine("total=${sizes.values.sum()}")
        }
        File(context.filesDir, "conversion-perf").apply { mkdirs() }
            .resolve("engine-retained-payload.txt")
            .writeText(report)
        println(report)
        assertTrue(sizes.getValue("systemDictionary") > 0L)
    }

    @Test
    fun measureEngineMemoryBreakdownOnDevice() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("conversionMemoryProbe") == "true")

        val phrase = arguments.getString("conversionPerfInput")
            ?: "このあぷりabc123へんかんこうほをこうそくにしたい"
        val context = ApplicationProvider.getApplicationContext<Context>()
        forceGc()
        val application = DetailedMemorySnapshot.capture("application")

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java,
        )
        forceGc()
        val component = DetailedMemorySnapshot.capture("hiltComponent")

        val engine = entryPoint.kanaKanjiEngine()
        forceGc()
        val engineLoaded = DetailedMemorySnapshot.capture("engineLoaded")

        val repository = entryPoint.userDictionaryRepository()
        val session = KanaKanjiConversionSession(
            engine,
            ConversionBackend.INCREMENTAL_SESSION,
        )
        phrase.indices.forEach { index ->
            session.query(
                request(
                    input = phrase.substring(0, index + 1),
                    repository = repository,
                    mode = CandidateQueryMode.PREDICTION,
                    bunsetsuSeparation = true,
                ),
            )
        }
        forceGc()
        val warmed = DetailedMemorySnapshot.capture("warmedSession")

        session.query(
            request(
                input = "andr",
                repository = repository,
                mode = CandidateQueryMode.PREDICTION,
                bunsetsuSeparation = true,
            ),
        )
        forceGc()
        val englishLoaded = DetailedMemorySnapshot.capture("englishLoaded")

        val snapshots = listOf(application, component, engineLoaded, warmed, englishLoaded)
        val report = buildString {
            appendLine(
                "device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} " +
                    "API ${android.os.Build.VERSION.SDK_INT}",
            )
            appendLine("phrase=$phrase")
            snapshots.forEach { appendLine(it.asReportLine()) }
            appendLine("engineDelta=${engineLoaded.deltaFrom(component).asReportLine()}")
            appendLine("warmupDelta=${warmed.deltaFrom(engineLoaded).asReportLine()}")
            appendLine("englishDelta=${englishLoaded.deltaFrom(warmed).asReportLine()}")
        }
        File(context.filesDir, "conversion-perf").apply { mkdirs() }
            .resolve("engine-memory-breakdown.txt")
            .writeText(report)
        println(report)
        assertTrue(session.query(request(phrase, repository, CandidateQueryMode.PREDICTION, true)).candidates.isNotEmpty())
    }

    @Test
    fun mixedAsciiPrefixesStayVisibleAndMatchLegacy() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java,
        )
        val engine = entryPoint.kanaKanjiEngine()
        val repository = entryPoint.userDictionaryRepository()
        val phrases = listOf(
            "こ123のあぷり",
            "こabcのあぷり",
            "123このあぷり",
            "abcこのあぷり",
            "こabc123のあぷり",
            "こ123abcのあぷり",
            "abc123このあぷり",
            "123abcこのあぷり",
            "このあぷり123へんかん",
            "このあぷりabcへんかん",
        )

        for (mode in listOf(
            CandidateQueryMode.PREDICTION,
            CandidateQueryMode.CONVERSION,
            CandidateQueryMode.NO_TAB_DEFAULT,
        )) {
            for (bunsetsu in listOf(false, true)) {
                for (phrase in phrases) {
                    val legacy = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY)
                    val incremental = KanaKanjiConversionSession(
                        engine,
                        ConversionBackend.INCREMENTAL_SESSION,
                    )
                    phrase.indices.forEach { index ->
                        val prefix = phrase.substring(0, index + 1)
                        val legacyResult = legacy.query(request(prefix, repository, mode, bunsetsu))
                        val incrementalResult = incremental.query(
                            request(prefix, repository, mode, bunsetsu),
                        )
                        val label = "$phrase prefix=$prefix mode=$mode bunsetsu=$bunsetsu"
                        assertTrue("$label legacy candidates empty", legacyResult.candidates.isNotEmpty())
                        assertTrue(
                            "$label incremental candidates empty",
                            incrementalResult.candidates.isNotEmpty(),
                        )
                        if (
                            prefix.any { it in '\u3041'..'\u3096' } &&
                            prefix.windowed(2).any { pair -> pair.all { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' } }
                        ) {
                            val hasFullLengthLatticeCandidate = incrementalResult.candidates.any {
                                it.type == 1.toByte() && it.length.toInt() == prefix.length
                            }
                            if (!hasFullLengthLatticeCandidate) {
                                val trace = engine.convertWithTrace(prefix)
                                fail(
                                    "$label has no full-length lattice conversion: " +
                                        "candidates=${incrementalResult.candidates.take(8)}, " +
                                        "unknownNodes=${trace.graphNodes.filter { it.source == "UNKNOWN" }}, " +
                                        "boundaries=${trace.boundaryEvents.filter { event ->
                                            event.leftTango.any { it.code < 128 } ||
                                                event.rightTango.any { it.code < 128 }
                                        }}",
                                )
                            }
                        }
                        assertEquals(
                            label,
                            legacyResult.candidates.take(20).map { it.fingerprint() },
                            incrementalResult.candidates.take(20).map { it.fingerprint() },
                        )
                        assertEquals(
                            "$label splits",
                            legacyResult.bunsetsuResult?.splitPatterns,
                            incrementalResult.bunsetsuResult?.splitPatterns,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun lazyEnglishDictionaryKeepsDictionaryPredictions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java,
        )
        val engine = entryPoint.kanaKanjiEngine()
        val repository = entryPoint.userDictionaryRepository()
        val result = KanaKanjiConversionSession(
            engine,
            ConversionBackend.INCREMENTAL_SESSION,
        ).query(
            request(
                input = "andr",
                repository = repository,
                mode = CandidateQueryMode.PREDICTION,
                bunsetsuSeparation = true,
            ),
        )

        assertTrue(
            "English dictionary prediction was lost after lazy loading: ${result.candidates.take(20)}",
            result.candidates.any { it.string.equals("andrew", ignoreCase = true) },
        )
    }

    @Test
    fun compareLegacyAndIncrementalSessionsOnDevice() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("incrementalConversionPerfProbe") == "true")

        val phrase = arguments.getString("conversionPerfInput")
            ?: "このあぷりのへんかんこうほをこうそくにしたい"
        val warmups = arguments.getString("conversionPerfWarmups")?.toIntOrNull() ?: 5
        val iterations = arguments.getString("conversionPerfIterations")?.toIntOrNull() ?: 30
        val mode = arguments.getString("conversionPerfMode")
            ?.let(CandidateQueryMode::valueOf)
            ?: CandidateQueryMode.PREDICTION
        val bunsetsuSeparation = arguments.getString("conversionPerfBunsetsu")
            ?.toBooleanStrictOrNull()
            ?: true
        val prefixes = phrase.indices.map { phrase.substring(0, it + 1) }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java,
        )
        val engine = entryPoint.kanaKanjiEngine()
        val repository = entryPoint.userDictionaryRepository()
        val disableSystemNgramForProbe =
            arguments.getString("conversionPerfSystemNgram") == "false"
        val loadedSystemNgram = SystemNgramRuntime.loadedDictionary()
        if (disableSystemNgramForProbe) {
            SystemNgramRuntime.disable()
        }

        assertModeTransitionParity(engine, repository)

        val legacyParity = runSequence(
            KanaKanjiConversionSession(engine, ConversionBackend.LEGACY),
            prefixes,
            repository,
            mode,
            bunsetsuSeparation,
            collectFingerprints = true,
        )
        val incrementalParity = runSequence(
            KanaKanjiConversionSession(engine, ConversionBackend.INCREMENTAL_SESSION),
            prefixes,
            repository,
            mode,
            bunsetsuSeparation,
            collectFingerprints = true,
        )
        assertEquals(legacyParity.fingerprints, incrementalParity.fingerprints)

        repeat(warmups) {
            runSequence(
                KanaKanjiConversionSession(engine, ConversionBackend.LEGACY),
                prefixes,
                repository,
                mode,
                bunsetsuSeparation,
            )
            runSequence(
                KanaKanjiConversionSession(engine, ConversionBackend.INCREMENTAL_SESSION),
                prefixes,
                repository,
                mode,
                bunsetsuSeparation,
            )
        }
        forceGc()
        val memoryBeforeMeasurements = MemorySnapshot.capture()

        val legacySessionSamples = ArrayList<Long>(iterations)
        val incrementalSessionSamples = ArrayList<Long>(iterations)
        val legacyCommandSamples = ArrayList<Long>(iterations * prefixes.size)
        val incrementalCommandSamples = ArrayList<Long>(iterations * prefixes.size)
        repeat(iterations) { iteration ->
            val order = if (iteration % 2 == 0) {
                listOf(ConversionBackend.LEGACY, ConversionBackend.INCREMENTAL_SESSION)
            } else {
                listOf(ConversionBackend.INCREMENTAL_SESSION, ConversionBackend.LEGACY)
            }
            order.forEach { backend ->
                val measurement = runSequence(
                    KanaKanjiConversionSession(engine, backend),
                    prefixes,
                    repository,
                    mode,
                    bunsetsuSeparation,
                )
                if (backend == ConversionBackend.LEGACY) {
                    legacySessionSamples += measurement.totalNs
                    legacyCommandSamples += measurement.commandSamplesNs
                } else {
                    incrementalSessionSamples += measurement.totalNs
                    incrementalCommandSamples += measurement.commandSamplesNs
                }
            }
        }

        val legacySession = Stats.from(legacySessionSamples)
        val incrementalSession = Stats.from(incrementalSessionSamples)
        val legacyCommand = Stats.from(legacyCommandSamples)
        val incrementalCommand = Stats.from(incrementalCommandSamples)
        val allocationIterations = 10
        val legacyAllocatedBytesPerSession = allocatedBytesPerSession(
            backend = ConversionBackend.LEGACY,
            iterations = allocationIterations,
            engine = engine,
            prefixes = prefixes,
            repository = repository,
            mode = mode,
            bunsetsuSeparation = bunsetsuSeparation,
        )
        val incrementalAllocatedBytesPerSession = allocatedBytesPerSession(
            backend = ConversionBackend.INCREMENTAL_SESSION,
            iterations = allocationIterations,
            engine = engine,
            prefixes = prefixes,
            repository = repository,
            mode = mode,
            bunsetsuSeparation = bunsetsuSeparation,
        )
        val stageBreakdown = if (bunsetsuSeparation) {
            measureIncrementalStages(
                iterations = 10,
                engine = engine,
                prefixes = prefixes,
                repository = repository,
                mode = mode,
            )
        } else {
            null
        }
        forceGc()
        val memoryAfterMeasurements = MemorySnapshot.capture()
        val report = buildString {
            appendLine("device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} API ${android.os.Build.VERSION.SDK_INT}")
            appendLine("phrase=$phrase")
            appendLine("prefixCount=${prefixes.size}")
            appendLine("warmups=$warmups")
            appendLine("iterations=$iterations")
            appendLine("bunsetsuSeparation=$bunsetsuSeparation")
            appendLine("mode=$mode")
            appendLine("optionalDictionaries=false")
            appendLine("typoCorrection=false")
            appendLine("omissionSearch=false")
            appendLine("candidateParityEveryPrefix=true")
            appendLine("legacySessionMs=${legacySession.asMilliseconds()}")
            appendLine("incrementalSessionMs=${incrementalSession.asMilliseconds()}")
            appendLine("legacyCommandMs=${legacyCommand.asMilliseconds()}")
            appendLine("incrementalCommandMs=${incrementalCommand.asMilliseconds()}")
            appendLine("allocationIterations=$allocationIterations")
            appendLine("legacyAllocatedBytesPerSession=$legacyAllocatedBytesPerSession")
            appendLine("incrementalAllocatedBytesPerSession=$incrementalAllocatedBytesPerSession")
            appendLine(
                "allocatedBytesReduction=" +
                    (1.0 - incrementalAllocatedBytesPerSession.toDouble() / legacyAllocatedBytesPerSession),
            )
            appendLine("javaHeapBeforeMeasurementsKb=${memoryBeforeMeasurements.javaHeapKb}")
            appendLine("javaHeapAfterMeasurementsKb=${memoryAfterMeasurements.javaHeapKb}")
            appendLine(
                "javaHeapMeasurementsDeltaKb=" +
                    (memoryAfterMeasurements.javaHeapKb - memoryBeforeMeasurements.javaHeapKb),
            )
            appendLine("totalPssBeforeMeasurementsKb=${memoryBeforeMeasurements.totalPssKb}")
            appendLine("totalPssAfterMeasurementsKb=${memoryAfterMeasurements.totalPssKb}")
            appendLine(
                "totalPssMeasurementsDeltaKb=" +
                    (memoryAfterMeasurements.totalPssKb - memoryBeforeMeasurements.totalPssKb),
            )
            appendLine("sessionP50Speedup=${legacySession.p50Ns.toDouble() / incrementalSession.p50Ns}")
            appendLine("sessionP95Speedup=${legacySession.p95Ns.toDouble() / incrementalSession.p95Ns}")
            appendLine("commandP50Speedup=${legacyCommand.p50Ns.toDouble() / incrementalCommand.p50Ns}")
            appendLine("commandP95Speedup=${legacyCommand.p95Ns.toDouble() / incrementalCommand.p95Ns}")
            stageBreakdown?.let { stages ->
                appendLine("incrementalGraphSessionMs=${stages.graph.asMilliseconds()}")
                appendLine("incrementalPenaltySessionMs=${stages.penalty.asMilliseconds()}")
                appendLine("incrementalForwardDpSessionMs=${stages.forwardDp.asMilliseconds()}")
                appendLine("incrementalBackwardSearchSessionMs=${stages.backwardSearch.asMilliseconds()}")
                appendLine("incrementalCandidateEnrichmentSessionMs=${stages.candidateEnrichment.asMilliseconds()}")
                appendLine("incrementalQueueElementsPerSession=${stages.queueElementsMean}")
                appendLine("incrementalStateRejectionsPerSession=${stages.stateRejectionsMean}")
                appendLine("incrementalExpansionCacheHitsPerSession=${stages.expansionCacheHitsMean}")
                appendLine("incrementalExpansionCacheMissesPerSession=${stages.expansionCacheMissesMean}")
            }
        }
        File(context.filesDir, "conversion-perf").apply { mkdirs() }
            .resolve(
                "incremental-session-${mode.name.lowercase()}-" +
                    "bunsetsu-$bunsetsuSeparation.txt",
            )
            .writeText(report)
        println(report)
        if (disableSystemNgramForProbe && loadedSystemNgram.ruleCount > 0) {
            SystemNgramRuntime.install(loadedSystemNgram)
        }
    }

    private suspend fun assertModeTransitionParity(
        engine: com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine,
        repository: UserDictionaryRepository,
    ) {
        val transitions = listOf(
            "き" to CandidateQueryMode.PREDICTION,
            "きょ" to CandidateQueryMode.PREDICTION,
            "きょう" to CandidateQueryMode.PREDICTION,
            "きょう" to CandidateQueryMode.CONVERSION,
            "きょう" to CandidateQueryMode.NO_TAB_DEFAULT,
            "きょう" to CandidateQueryMode.EISUKANA,
            "きょうは" to CandidateQueryMode.PREDICTION,
        )
        for (bunsetsu in listOf(false, true)) {
            val legacy = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY)
            val incremental = KanaKanjiConversionSession(
                engine,
                ConversionBackend.INCREMENTAL_SESSION,
            )
            for ((input, mode) in transitions) {
                val legacyResult = legacy.query(request(input, repository, mode, bunsetsu))
                val incrementalResult = incremental.query(request(input, repository, mode, bunsetsu))
                assertEquals(
                    "$input/$mode/bunsetsu=$bunsetsu",
                    legacyResult.candidates.take(20).map { it.fingerprint() },
                    incrementalResult.candidates.take(20).map { it.fingerprint() },
                )
                assertEquals(
                    "$input/$mode/bunsetsu=$bunsetsu splits",
                    legacyResult.bunsetsuResult?.splitPatterns,
                    incrementalResult.bunsetsuResult?.splitPatterns,
                )
            }
        }
    }

    private suspend fun allocatedBytesPerSession(
        backend: ConversionBackend,
        iterations: Int,
        engine: com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine,
        prefixes: List<String>,
        repository: UserDictionaryRepository,
        mode: CandidateQueryMode,
        bunsetsuSeparation: Boolean,
    ): Long {
        Runtime.getRuntime().gc()
        val before = runtimeStat("art.gc.bytes-allocated")
        repeat(iterations) {
            runSequence(
                KanaKanjiConversionSession(engine, backend),
                prefixes,
                repository,
                mode,
                bunsetsuSeparation,
            )
        }
        return (runtimeStat("art.gc.bytes-allocated") - before) / iterations
    }

    private fun runtimeStat(name: String): Long =
        Debug.getRuntimeStat(name)?.toLongOrNull() ?: 0L

    private fun groupsCoveredField(name: String): Boolean =
        name == "connectionMatrix" ||
            (name.startsWith("system") && !name.startsWith("systemUser")) ||
            name.startsWith("singleKanji") ||
            name.startsWith("emoji") ||
            name.startsWith("emoticon") ||
            name.startsWith("symbol") ||
            name.startsWith("readingCorrection") ||
            name.startsWith("kotowaza") ||
            name == "englishEngine" ||
            name.startsWith("mozc") ||
            name == "graphBuilder" ||
            name == "findPath"

    private fun retainedPayloadBytes(value: Any?, visited: MutableSet<Any>): Long {
        if (value == null || !visited.add(value)) return 0L
        return when (value) {
            is ByteArray -> value.size.toLong()
            is BooleanArray -> value.size.toLong()
            is ShortArray -> value.size.toLong() * Short.SIZE_BYTES
            is CharArray -> value.size.toLong() * Char.SIZE_BYTES
            is IntArray -> value.size.toLong() * Int.SIZE_BYTES
            is FloatArray -> value.size.toLong() * Float.SIZE_BYTES
            is LongArray -> value.size.toLong() * Long.SIZE_BYTES
            is DoubleArray -> value.size.toLong() * Double.SIZE_BYTES
            is BitSet -> value.size().toLong() / Byte.SIZE_BITS
            is String -> value.length.toLong() * Char.SIZE_BYTES
            is Array<*> -> value.sumOf { retainedPayloadBytes(it, visited) }
            is Iterable<*> -> value.sumOf { retainedPayloadBytes(it, visited) }
            is Map<*, *> -> value.entries.sumOf { entry ->
                retainedPayloadBytes(entry.key, visited) + retainedPayloadBytes(entry.value, visited)
            }
            else -> {
                val packageName = value.javaClass.packageName
                if (!packageName.startsWith("com.kazumaproject")) {
                    0L
                } else {
                    generateSequence(value.javaClass) { it.superclass }
                        .takeWhile { it != Any::class.java }
                        .flatMap { it.declaredFields.asSequence() }
                        .filterNot { Modifier.isStatic(it.modifiers) || it.type.isPrimitive }
                        .sumOf { field ->
                            runCatching {
                                field.isAccessible = true
                                retainedPayloadBytes(field.get(value), visited)
                            }.getOrDefault(0L)
                        }
                }
            }
        }
    }

    private fun forceGc() {
        repeat(3) {
            System.gc()
            System.runFinalization()
            Thread.sleep(50)
        }
    }

    private data class MemorySnapshot(
        val javaHeapKb: Long,
        val totalPssKb: Int,
    ) {
        companion object {
            fun capture(): MemorySnapshot {
                val runtime = Runtime.getRuntime()
                val memoryInfo = Debug.MemoryInfo()
                Debug.getMemoryInfo(memoryInfo)
                return MemorySnapshot(
                    javaHeapKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L,
                    totalPssKb = memoryInfo.totalPss,
                )
            }
        }
    }

    private data class DetailedMemorySnapshot(
        val label: String,
        val runtimeJavaHeapKb: Long,
        val totalPssKb: Int,
        val javaHeapPssKb: Int,
        val nativeHeapPssKb: Int,
        val codePssKb: Int,
        val stackPssKb: Int,
        val graphicsPssKb: Int,
        val privateOtherPssKb: Int,
        val systemPssKb: Int,
        val totalSwapKb: Int,
    ) {
        fun deltaFrom(before: DetailedMemorySnapshot): DetailedMemorySnapshot =
            DetailedMemorySnapshot(
                label = "$label-${before.label}",
                runtimeJavaHeapKb = runtimeJavaHeapKb - before.runtimeJavaHeapKb,
                totalPssKb = totalPssKb - before.totalPssKb,
                javaHeapPssKb = javaHeapPssKb - before.javaHeapPssKb,
                nativeHeapPssKb = nativeHeapPssKb - before.nativeHeapPssKb,
                codePssKb = codePssKb - before.codePssKb,
                stackPssKb = stackPssKb - before.stackPssKb,
                graphicsPssKb = graphicsPssKb - before.graphicsPssKb,
                privateOtherPssKb = privateOtherPssKb - before.privateOtherPssKb,
                systemPssKb = systemPssKb - before.systemPssKb,
                totalSwapKb = totalSwapKb - before.totalSwapKb,
            )

        fun asReportLine(): String =
            "$label(runtimeJavaHeapKb=$runtimeJavaHeapKb,totalPssKb=$totalPssKb," +
                "javaHeapPssKb=$javaHeapPssKb,nativeHeapPssKb=$nativeHeapPssKb," +
                "codePssKb=$codePssKb,stackPssKb=$stackPssKb,graphicsPssKb=$graphicsPssKb," +
                "privateOtherPssKb=$privateOtherPssKb,systemPssKb=$systemPssKb," +
                "totalSwapKb=$totalSwapKb)"

        companion object {
            fun capture(label: String): DetailedMemorySnapshot {
                val runtime = Runtime.getRuntime()
                val memoryInfo = Debug.MemoryInfo()
                Debug.getMemoryInfo(memoryInfo)
                fun stat(name: String): Int = memoryInfo.getMemoryStat(name)?.toIntOrNull() ?: 0
                return DetailedMemorySnapshot(
                    label = label,
                    runtimeJavaHeapKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L,
                    totalPssKb = memoryInfo.totalPss,
                    javaHeapPssKb = stat("summary.java-heap"),
                    nativeHeapPssKb = stat("summary.native-heap"),
                    codePssKb = stat("summary.code"),
                    stackPssKb = stat("summary.stack"),
                    graphicsPssKb = stat("summary.graphics"),
                    privateOtherPssKb = stat("summary.private-other"),
                    systemPssKb = stat("summary.system"),
                    totalSwapKb = stat("summary.total-swap"),
                )
            }
        }
    }

    private suspend fun measureIncrementalStages(
        iterations: Int,
        engine: com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine,
        prefixes: List<String>,
        repository: UserDictionaryRepository,
        mode: CandidateQueryMode,
    ): StageBreakdown {
        val graph = ArrayList<Long>(iterations)
        val penalty = ArrayList<Long>(iterations)
        val forwardDp = ArrayList<Long>(iterations)
        val backwardSearch = ArrayList<Long>(iterations)
        val candidateEnrichment = ArrayList<Long>(iterations)
        val queueElements = ArrayList<Long>(iterations)
        val stateRejections = ArrayList<Long>(iterations)
        val expansionCacheHits = ArrayList<Long>(iterations)
        val expansionCacheMisses = ArrayList<Long>(iterations)
        repeat(iterations) {
            val session = KanaKanjiConversionSession(
                engine,
                ConversionBackend.INCREMENTAL_SESSION,
            ).apply { enablePerformanceProbe() }
            var graphNs = 0L
            var penaltyNs = 0L
            var forwardDpNs = 0L
            var backwardSearchNs = 0L
            var queueElementCount = 0L
            var stateRejectionCount = 0L
            var expansionCacheHitCount = 0L
            var expansionCacheMissCount = 0L
            val totalNs = measureNanoTime {
                prefixes.forEach { prefix ->
                    session.query(request(prefix, repository, mode, bunsetsuSeparation = true))
                    checkNotNull(session.performanceSnapshot()).let { snapshot ->
                        graphNs += snapshot.graphNs
                        penaltyNs += snapshot.penaltyNs
                        forwardDpNs += snapshot.forwardDpNs
                        backwardSearchNs += snapshot.backwardSearchNs
                        queueElementCount += snapshot.queueElementCount
                        stateRejectionCount += snapshot.stateRejectionCount
                        expansionCacheHitCount += snapshot.expansionCacheHitCount
                        expansionCacheMissCount += snapshot.expansionCacheMissCount
                    }
                }
            }
            graph += graphNs
            penalty += penaltyNs
            forwardDp += forwardDpNs
            backwardSearch += backwardSearchNs
            candidateEnrichment +=
                (totalNs - graphNs - penaltyNs - forwardDpNs - backwardSearchNs).coerceAtLeast(0L)
            queueElements += queueElementCount
            stateRejections += stateRejectionCount
            expansionCacheHits += expansionCacheHitCount
            expansionCacheMisses += expansionCacheMissCount
        }
        return StageBreakdown(
            graph = Stats.from(graph),
            penalty = Stats.from(penalty),
            forwardDp = Stats.from(forwardDp),
            backwardSearch = Stats.from(backwardSearch),
            candidateEnrichment = Stats.from(candidateEnrichment),
            queueElementsMean = queueElements.average(),
            stateRejectionsMean = stateRejections.average(),
            expansionCacheHitsMean = expansionCacheHits.average(),
            expansionCacheMissesMean = expansionCacheMisses.average(),
        )
    }

    private suspend fun runSequence(
        session: KanaKanjiConversionSession,
        prefixes: List<String>,
        repository: UserDictionaryRepository,
        mode: CandidateQueryMode,
        bunsetsuSeparation: Boolean,
        collectFingerprints: Boolean = false,
    ): SequenceMeasurement {
        val commandSamples = ArrayList<Long>(prefixes.size)
        val fingerprints = if (collectFingerprints) ArrayList<List<String>>(prefixes.size) else null
        val totalNs = measureNanoTime {
            prefixes.forEach { prefix ->
                lateinit var candidates: List<Candidate>
                commandSamples += measureNanoTime {
                    candidates = session.query(
                        request(prefix, repository, mode, bunsetsuSeparation),
                    ).candidates
                }
                fingerprints?.add(candidates.take(20).map { it.fingerprint() })
            }
        }
        return SequenceMeasurement(totalNs, commandSamples, fingerprints.orEmpty())
    }

    private fun request(
        input: String,
        repository: UserDictionaryRepository,
        mode: CandidateQueryMode,
        bunsetsuSeparation: Boolean,
    ) = KanaKanjiQueryRequest(
        input = input,
        mode = mode,
        bunsetsuSeparation = bunsetsuSeparation,
        n = 4,
        mozcUtPersonName = false,
        mozcUtPlaces = false,
        mozcUtWiki = false,
        mozcUtNeologd = false,
        mozcUtWeb = false,
        userDictionaryRepository = repository,
        learnRepository = null,
        omissionSearchEnabled = false,
        typoCorrectionJapaneseFlickEnabled = false,
        typoCorrectionQwertyEnglishEnabled = false,
        typoCorrectionOffsetScore = 3000,
        omissionSearchOffsetScore = 1900,
        beamWidth = 20,
    )

    private fun Candidate.fingerprint(): String =
        "$string\u001f$type\u001f$length\u001f$score\u001f${yomi.orEmpty()}\u001f$leftId\u001f$rightId"

    private data class SequenceMeasurement(
        val totalNs: Long,
        val commandSamplesNs: List<Long>,
        val fingerprints: List<List<String>>,
    )

    private data class StageBreakdown(
        val graph: Stats,
        val penalty: Stats,
        val forwardDp: Stats,
        val backwardSearch: Stats,
        val candidateEnrichment: Stats,
        val queueElementsMean: Double,
        val stateRejectionsMean: Double,
        val expansionCacheHitsMean: Double,
        val expansionCacheMissesMean: Double,
    )

    private data class Stats(
        val meanNs: Double,
        val p50Ns: Long,
        val p95Ns: Long,
        val p99Ns: Long,
    ) {
        fun asMilliseconds(): String =
            "mean=${meanNs / 1_000_000.0},p50=${p50Ns / 1_000_000.0}," +
                "p95=${p95Ns / 1_000_000.0},p99=${p99Ns / 1_000_000.0}"

        companion object {
            fun from(samples: List<Long>): Stats {
                val sorted = samples.sorted()
                fun percentile(fraction: Double): Long {
                    val index = (ceil(sorted.size * fraction).toInt() - 1).coerceIn(sorted.indices)
                    return sorted[index]
                }
                return Stats(
                    meanNs = samples.average(),
                    p50Ns = percentile(0.50),
                    p95Ns = percentile(0.95),
                    p99Ns = percentile(0.99),
                )
            }
        }
    }
}
