package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_ENGLISH
import com.kazumaproject.markdownhelperkeyboard.converter.compact.CompactEnglishDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.english.phrase.EnglishPhraseDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.english.phrase.GrammarRoles
import com.kazumaproject.markdownhelperkeyboard.converter.english.phrase.PhraseConverter
import com.kazumaproject.markdownhelperkeyboard.converter.english.phrase.lookupKey
import com.kazumaproject.markdownhelperkeyboard.converter.english.phrase.phraseContinuations
import com.kazumaproject.markdownhelperkeyboard.converter.english.louds.LOUDS
import com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId
import com.kazumaproject.markdownhelperkeyboard.converter.english.tokenArray.TokenArray
import com.kazumaproject.markdownhelperkeyboard.BuildConfig
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlideDecodeOptions
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlideDecoder
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlideDecodeMetrics
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlideDictionaryEntry
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlideCandidateCaseExpander
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlideCandidateProvider
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlideIndexedDictionaryProvider
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlidePrebuiltDictionaryLoader
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlidePrebuiltLoadResult
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryBinaryReader
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileKey
import com.kazumaproject.qwerty_keyboard.glide.QwertyInputPointers
import com.kazumaproject.qwerty_keyboard.glide.QwertyKeyboardProximityInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

internal fun resolveCandidateCase(input: String, candidate: String): String {
    val inputLetters = input.filter(Char::isLetter)
    val candidateLetters = candidate.filter(Char::isLetter)

    return when {
        inputLetters.isNotEmpty() && inputLetters.all(Char::isUpperCase) -> candidate.uppercase()
        candidate.drop(1).any(Char::isUpperCase) ||
            candidateLetters.isNotEmpty() && candidateLetters.all(Char::isUpperCase) -> candidate
        input.any(Char::isUpperCase) && candidate.startsWith(input, ignoreCase = true) ->
            input + candidate.drop(input.length)
        else -> candidate
    }
}

internal fun candidateCaseVariants(input: String, candidate: String): List<Pair<String, Int>> =
    listOf(
        resolveCandidateCase(input, candidate) to 0,
        candidate to 500,
        candidate.replaceFirstChar { it.uppercaseChar() } to 500,
        candidate.uppercase() to 2000
    ).distinctBy { (surface, _) -> surface }

class EnglishEngine : QwertyGlideCandidateProvider {
    @Volatile
    private var dictionaryData: EnglishDictionaryData? = null
    @Volatile
    private var dictionaryLoader: (() -> EnglishDictionaryData)? = null
    @Volatile
    private var qwertyGlideDecoder: QwertyGlideDecoder? = null
    @Volatile
    private var qwertyFallbackGlideDecoder: QwertyGlideDecoder? = null
    @Volatile
    private var qwertyGlideDictionaryReady: Boolean = false
    @Volatile
    private var qwertyGlideWarmupJob: Job? = null
    @Volatile
    private var qwertyGlideInputEnabled: Boolean = false
    private val qwertyGlideCandidateCaseExpander = QwertyGlideCandidateCaseExpander()
    private val qwertyGlideWarmupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun buildEngine(
        englishReadingLOUDS: LOUDSWithTermId,
        englishWordLOUDS: LOUDS,
        englishTokenArray: TokenArray,
        englishSuccinctBitVectorLBSReading: SuccinctBitVector,
        englishSuccinctBitVectorLBSWord: SuccinctBitVector,
        englishSuccinctBitVectorReadingIsLeaf: SuccinctBitVector,
        englishSuccinctBitVectorTokenArray: SuccinctBitVector,
    ) {
        dictionaryData = EnglishDictionaryData(
            CompactEnglishDictionary(
                englishReadingLOUDS, englishWordLOUDS, englishTokenArray,
                englishSuccinctBitVectorLBSReading, englishSuccinctBitVectorReadingIsLeaf,
                englishSuccinctBitVectorLBSWord, englishSuccinctBitVectorTokenArray,
            ),
        )
        dictionaryLoader = null
        qwertyGlideDictionaryReady = false
        qwertyGlideDecoder = null
        qwertyGlideInputEnabled = false
        qwertyFallbackGlideDecoder = createQwertyGlideDecoder(
            entries = fallbackGlideDictionaryEntries(),
            dictionaryReady = false
        )
    }

    fun configureLazyDictionaryLoading(reader: DictionaryBinaryReader) {
        dictionaryLoader = { loadDictionaryData(reader) }
        dictionaryData = null
        qwertyGlideDictionaryReady = false
        qwertyGlideDecoder = null
        qwertyGlideInputEnabled = false
        qwertyFallbackGlideDecoder = createQwertyGlideDecoder(
            entries = fallbackGlideDictionaryEntries(),
            dictionaryReady = false,
        )
    }

    override suspend fun getGlideCandidates(
        inputPointers: QwertyInputPointers,
        proximityInfo: QwertyKeyboardProximityInfo,
        previousText: String,
        limit: Int
    ): List<Candidate> {
        if (limit <= 0 || inputPointers.points.size < 2 || proximityInfo.keys.isEmpty()) return emptyList()
        if (!qwertyGlideInputEnabled) return emptyList()
        val decoder = qwertyGlideDecoder ?: run {
            warmUpQwertyGlideDecoderAsync()
            getOrCreateFallbackQwertyGlideDecoder()
        }
        val baseCandidates = decoder.decode(
            inputPointers = inputPointers,
            proximityInfo = proximityInfo,
            previousText = previousText,
            limit = (limit * 3).coerceAtLeast(limit)
        )
        return qwertyGlideCandidateCaseExpander.expand(baseCandidates, limit)
    }

    fun warmUpQwertyGlideDecoderAsync() {
        if (!qwertyGlideInputEnabled) {
            logQwertyGlidePrebuilt("QWERTY glide preference disabled, skip runtime warmup")
            return
        }
        if (qwertyGlideDictionaryReady && qwertyGlideDecoder != null) return
        synchronized(this) {
            val existing = qwertyGlideWarmupJob
            if (existing?.isActive == true) return
            qwertyGlideWarmupJob = qwertyGlideWarmupScope.launch {
                val startedAt = System.nanoTime()
                val entries = buildGlideDictionaryEntries()
                val decoder = createQwertyGlideDecoder(
                    entries = entries,
                    dictionaryReady = true
                )
                if (!isActive) return@launch
                synchronized(this@EnglishEngine) {
                    if (!isActive) return@launch
                    qwertyGlideDecoder = decoder
                    qwertyGlideDictionaryReady = true
                }
                if (BuildConfig.DEBUG) {
                    Timber.d(
                        "QWERTY glide dictionary warmup complete: entries=${entries.size} elapsed_ms=${(System.nanoTime() - startedAt) / 1_000_000L}"
                    )
                }
            }
        }
    }

    fun reloadDictionariesFromCurrentSources(reader: DictionaryBinaryReader) {
        reloadDictionariesFromCurrentSources(
            reader = reader,
            qwertyGlideInputEnabled = qwertyGlideInputEnabled,
            qwertyGlidePrebuiltDictionaryLoader = null,
            canUseBundledPrebuiltIndex = false,
        )
    }

    fun reloadDictionariesFromCurrentSources(
        reader: DictionaryBinaryReader,
        qwertyGlideInputEnabled: Boolean,
        qwertyGlidePrebuiltDictionaryLoader: QwertyGlidePrebuiltDictionaryLoader?,
        canUseBundledPrebuiltIndex: Boolean,
    ) {
        synchronized(this) {
            cancelQwertyGlideWarmup()
            dictionaryLoader = { loadDictionaryData(reader) }
            dictionaryData = null
            this.qwertyGlideInputEnabled = qwertyGlideInputEnabled
            qwertyGlideDictionaryReady = false
            qwertyGlideDecoder = null
            qwertyFallbackGlideDecoder = createQwertyGlideDecoder(
                entries = fallbackGlideDictionaryEntries(),
                dictionaryReady = false,
            )
        }
        configureQwertyGlideDecoder(
            enabled = qwertyGlideInputEnabled,
            canUseBundledPrebuiltIndex = canUseBundledPrebuiltIndex,
            prebuiltDictionaryLoader = qwertyGlidePrebuiltDictionaryLoader,
        )
    }

    fun isQwertyGlideDictionaryReady(): Boolean = qwertyGlideDictionaryReady

    fun isQwertyGlideInputEnabled(): Boolean = qwertyGlideInputEnabled

    fun hasQwertyGlideDecoder(): Boolean = qwertyGlideDecoder != null

    fun isQwertyGlideWarmupActive(): Boolean = qwertyGlideWarmupJob?.isActive == true

    fun configureQwertyGlideDecoder(
        enabled: Boolean,
        canUseBundledPrebuiltIndex: Boolean,
        prebuiltDictionaryLoader: QwertyGlidePrebuiltDictionaryLoader?,
    ) {
        if (!enabled) {
            synchronized(this) {
                qwertyGlideInputEnabled = false
                cancelQwertyGlideWarmup()
                qwertyGlideDecoder = null
                qwertyGlideDictionaryReady = false
            }
            logQwertyGlidePrebuilt("QWERTY glide preference disabled, skip prebuilt load")
            return
        }

        if (
            canUseBundledPrebuiltIndex &&
            qwertyGlideInputEnabled &&
            qwertyGlideDictionaryReady &&
            qwertyGlideDecoder != null
        ) {
            return
        }

        synchronized(this) {
            qwertyGlideInputEnabled = true
            cancelQwertyGlideWarmup()
            qwertyGlideDecoder = null
            qwertyGlideDictionaryReady = false
        }

        if (!canUseBundledPrebuiltIndex || prebuiltDictionaryLoader == null) {
            logQwertyGlidePrebuilt("External English dictionary override active, skip bundled prebuilt glide index")
            logQwertyGlidePrebuilt("Fallback to runtime glide dictionary build")
            return
        }

        when (val result = prebuiltDictionaryLoader.load()) {
            is QwertyGlidePrebuiltLoadResult.Loaded -> {
                val decoder = createQwertyGlideDecoder(
                    provider = result.provider,
                    dictionaryReady = true,
                )
                synchronized(this) {
                    if (!qwertyGlideInputEnabled) return
                    qwertyGlideDecoder = decoder
                    qwertyGlideDictionaryReady = true
                }
                logQwertyGlidePrebuilt("Bundled prebuilt glide index loaded: entries=${result.provider.entryCount}")
            }

            is QwertyGlidePrebuiltLoadResult.NotAvailable -> {
                synchronized(this) {
                    qwertyGlideDecoder = null
                    qwertyGlideDictionaryReady = false
                }
                logQwertyGlidePrebuilt("Bundled prebuilt glide index unavailable: ${result.reason}")
                logQwertyGlidePrebuilt("Fallback to runtime glide dictionary build")
            }

            is QwertyGlidePrebuiltLoadResult.Invalid -> {
                synchronized(this) {
                    qwertyGlideDecoder = null
                    qwertyGlideDictionaryReady = false
                }
                logQwertyGlidePrebuilt("Bundled prebuilt glide index invalid: ${result.reason}")
                logQwertyGlidePrebuilt("Fallback to runtime glide dictionary build")
            }
        }
    }

    /**
     * Loads the bundled glide index away from the IME main thread.
     *
     * Until the index is ready, glide input continues to use the small fallback decoder. Repeated
     * input sessions share the in-flight job instead of restarting the same asset read.
     */
    fun configureQwertyGlideDecoderAsync(
        enabled: Boolean,
        canUseBundledPrebuiltIndex: Boolean,
        prebuiltDictionaryLoader: QwertyGlidePrebuiltDictionaryLoader?,
    ) {
        if (!enabled) {
            configureQwertyGlideDecoder(
                enabled = false,
                canUseBundledPrebuiltIndex = canUseBundledPrebuiltIndex,
                prebuiltDictionaryLoader = prebuiltDictionaryLoader,
            )
            return
        }

        synchronized(this) {
            if (
                canUseBundledPrebuiltIndex &&
                qwertyGlideInputEnabled &&
                (qwertyGlideDictionaryReady && qwertyGlideDecoder != null ||
                    qwertyGlideWarmupJob?.isActive == true)
            ) {
                return
            }
            qwertyGlideInputEnabled = true
            cancelQwertyGlideWarmup()
            qwertyGlideDecoder = null
            qwertyGlideDictionaryReady = false
        }

        if (!canUseBundledPrebuiltIndex || prebuiltDictionaryLoader == null) {
            logQwertyGlidePrebuilt(
                "External English dictionary override active, skip bundled prebuilt glide index"
            )
            logQwertyGlidePrebuilt("Fallback to runtime glide dictionary build")
            return
        }

        val job = qwertyGlideWarmupScope.launch(start = CoroutineStart.LAZY) {
            val startedAt = System.nanoTime()
            val result = prebuiltDictionaryLoader.load()
            if (!isActive) return@launch
            when (result) {
                is QwertyGlidePrebuiltLoadResult.Loaded -> {
                    val decoder = createQwertyGlideDecoder(
                        provider = result.provider,
                        dictionaryReady = true,
                    )
                    if (!isActive) return@launch
                    synchronized(this@EnglishEngine) {
                        if (!isActive || !qwertyGlideInputEnabled) return@synchronized
                        qwertyGlideDecoder = decoder
                        qwertyGlideDictionaryReady = true
                    }
                    logQwertyGlidePrebuilt(
                        "Bundled prebuilt glide index loaded asynchronously: " +
                            "entries=${result.provider.entryCount} " +
                            "elapsed_ms=${(System.nanoTime() - startedAt) / 1_000_000L}"
                    )
                }

                is QwertyGlidePrebuiltLoadResult.NotAvailable -> {
                    logQwertyGlidePrebuilt(
                        "Bundled prebuilt glide index unavailable: ${result.reason}"
                    )
                    logQwertyGlidePrebuilt("Fallback to runtime glide dictionary build")
                }

                is QwertyGlidePrebuiltLoadResult.Invalid -> {
                    logQwertyGlidePrebuilt(
                        "Bundled prebuilt glide index invalid: ${result.reason}"
                    )
                    logQwertyGlidePrebuilt("Fallback to runtime glide dictionary build")
                }
            }
        }
        synchronized(this) {
            qwertyGlideWarmupJob = job
        }
        job.start()
    }

    internal suspend fun awaitQwertyGlideWarmup() {
        qwertyGlideWarmupJob?.join()
    }

    fun cancelQwertyGlideWarmup() {
        qwertyGlideWarmupJob?.cancel()
        qwertyGlideWarmupJob = null
    }

    fun releaseQwertyGlideResources() {
        cancelQwertyGlideWarmup()
        qwertyGlideDecoder = null
        qwertyFallbackGlideDecoder = null
        qwertyGlideDictionaryReady = false
        qwertyGlideInputEnabled = false
    }

    fun invalidateQwertyGlideCache() {
        qwertyGlideDecoder?.clearCache()
        qwertyFallbackGlideDecoder?.clearCache()
    }

    private fun getOrCreateFallbackQwertyGlideDecoder(): QwertyGlideDecoder {
        qwertyFallbackGlideDecoder?.let { return it }
        return synchronized(this) {
            qwertyFallbackGlideDecoder ?: createQwertyGlideDecoder(
                entries = fallbackGlideDictionaryEntries(),
                dictionaryReady = false
            ).also { qwertyFallbackGlideDecoder = it }
        }
    }

    private fun createQwertyGlideDecoder(
        entries: Iterable<QwertyGlideDictionaryEntry>,
        dictionaryReady: Boolean
    ): QwertyGlideDecoder {
        return createQwertyGlideDecoder(
            provider = QwertyGlideIndexedDictionaryProvider(entries),
            dictionaryReady = dictionaryReady,
        )
    }

    private fun createQwertyGlideDecoder(
        provider: QwertyGlideIndexedDictionaryProvider,
        dictionaryReady: Boolean
    ): QwertyGlideDecoder {
        return QwertyGlideDecoder(
            dictionaryProvider = provider,
            options = QwertyGlideDecodeOptions(),
            dictionaryReady = dictionaryReady,
            metricsListener = ::logQwertyGlideMetrics
        )
    }

    private fun logQwertyGlidePrebuilt(message: String) {
        if (BuildConfig.DEBUG) {
            Timber.d(message)
        }
    }

    private fun logQwertyGlideMetrics(metrics: QwertyGlideDecodeMetrics) {
        if (!BuildConfig.DEBUG) return
        Timber.d(
            "QWERTY glide decode: dictionary_ready=${metrics.dictionaryReady} " +
                    "raw_bucket_candidate_count=${metrics.rawBucketCandidateCount} " +
                    "prefilter_candidate_count=${metrics.prefilterCandidateCount} " +
                    "full_score_candidate_count=${metrics.fullScoreCandidateCount} " +
                    "rerank_candidate_count=${metrics.rerankCandidateCount} " +
                    "decode_total_ms=${metrics.decodeTotalMs} prefilter_ms=${metrics.prefilterMs} " +
                    "full_score_ms=${metrics.fullScoreMs} rerank_ms=${metrics.rerankMs} " +
                    "cache_hit=${metrics.cacheHit}"
        )
    }

    private fun fallbackGlideDictionaryEntries(): List<QwertyGlideDictionaryEntry> {
        return listOf(
            "hello", "good", "test", "word", "world", "keyboard", "android", "sumire",
            "coffee", "letter", "people", "glide", "time", "home", "something"
        ).map { word -> QwertyGlideDictionaryEntry(word, 6000) }
    }

    private fun buildGlideDictionaryEntries(): List<QwertyGlideDictionaryEntry> {
        val dictionary = ensureDictionariesLoaded().dictionary
        val entries = linkedMapOf<String, QwertyGlideDictionaryEntry>()
        val readings = dictionary.readingTrie.predictiveSearch(
            prefix = "",
            succinctBitVector = dictionary.readingLbsIndex
        )
        for (reading in readings) {
            if (reading.length !in 2..24 || !reading.all { it in 'a'..'z' }) continue
            val nodeIndex = dictionary.readingTrie.getNodeIndex(
                reading,
                succinctBitVector = dictionary.readingLbsIndex
            )
            if (nodeIndex <= 0) continue
            val termId = dictionary.readingTrie.getTermId(
                nodeIndex,
                succinctBitVector = dictionary.readingLeafIndex
            )
            if (termId < 0) continue
            val tokens = dictionary.tokenArray.getListDictionaryByYomiTermId(
                termId,
                succinctBitVector = dictionary.tokenIndex
            )
            if (tokens.isEmpty()) {
                entries.mergeGlideEntry(reading, 9000)
            } else {
                for (entry in tokens) {
                    val word = when (entry.nodeId) {
                        -1 -> reading
                        else -> dictionary.wordTrie.getLetter(
                            entry.nodeId,
                            succinctBitVector = dictionary.wordLbsIndex
                        )
                    }.lowercase()
                    if (word.length in 2..24 && word.all { it in 'a'..'z' }) {
                        entries.mergeGlideEntry(word, entry.wordCost.toInt())
                    }
                }
            }
        }
        fallbackGlideDictionaryEntries().forEach { entry ->
            entries.mergeGlideEntry(entry.word, entry.wordCost)
        }
        return entries.values.toList()
    }

    fun getCandidates(
        input: String,
        enableTypoCorrection: Boolean = false,
    ): List<Candidate> = getCandidates(
        input = input,
        enableTypoCorrection = enableTypoCorrection,
        enablePrediction = true,
    )

    fun getCandidates(
        input: String,
        enableTypoCorrection: Boolean,
        enablePrediction: Boolean,
    ): List<Candidate> {
        if (input.isEmpty()) return emptyList()
        return getCandidates(input, enableTypoCorrection, enablePrediction, ensureDictionariesLoaded().dictionary)
    }

    internal fun getPhraseContinuations(committedText: String): List<String> {
        if (committedText.length !in 1..254) return emptyList()
        val prefix = lookupKey(committedText.trimStart(' '))
        if (prefix.endsWith(' ') || prefix.none(Char::isLetter)) return emptyList()
        if (prefix.any { char ->
                char !in '0'..'9' && char !in " '-" &&
                    Character.UnicodeScript.of(char.code) != Character.UnicodeScript.LATIN &&
                    Character.getType(char) != Character.NON_SPACING_MARK.toInt()
            }
        ) return emptyList()
        return ensureDictionariesLoaded().dictionary.phraseContinuations(prefix, limit = 48)
    }

    internal fun getConversionCandidates(
        input: String,
        legacyInput: String?,
        enableTypoCorrection: Boolean,
        enablePrediction: Boolean,
        prefixMode: Boolean,
        fallback: List<Candidate>,
    ): List<Candidate> {
        val usePhrases = enablePrediction && input.length in 2..255 &&
            input.any(Char::isLetter) && input.all { char ->
                if (char.isLetter()) Character.UnicodeScript.of(char.code) == Character.UnicodeScript.LATIN
                else char in ' '..'~' || char == '’' || char == '\t' ||
                    Character.getType(char) == Character.NON_SPACING_MARK.toInt()
            }
        if (legacyInput == null && !usePhrases) return fallback
        val data = ensureDictionariesLoaded()
        val legacy = if (legacyInput != null) {
            // The Conversion tab uses the English setting for exact phrases while
            // leaving single-word completion to the prediction modes.
            getCandidates(legacyInput, enableTypoCorrection, enablePrediction && prefixMode, data.dictionary)
        } else {
            fallback
        }
        if (!usePhrases) return legacy
        val phraseConverter = data.phraseConverter ?: return legacy
        val phraseTexts = phraseConverter.convert(input, prefixMode, limit = 12)
            .asSequence()
            .map { phrase ->
                val text = resolveCandidateCase(input, phrase.text)
                if (input.first().isUpperCase() && text.none(Char::isUpperCase)) {
                    text.replaceFirstChar(Char::uppercaseChar)
                } else {
                    text
                }
            }
            .filter { it != input }
            .distinct()
            .toList()
        if (phraseTexts.isEmpty()) return legacy

        val seen = hashSetOf(input)
        return buildList {
            add(Candidate(input, CANDIDATE_TYPE_ENGLISH, input.length.toUByte(), 500))
            phraseTexts.forEachIndexed { index, text ->
                if (seen.add(text)) {
                    add(Candidate(text, CANDIDATE_TYPE_ENGLISH, input.length.toUByte(), 501 + index))
                }
            }
            legacy.forEach { candidate ->
                if (seen.add(candidate.string)) add(candidate.copy(score = maxOf(513, candidate.score)))
            }
        }
    }

    private fun getCandidates(
        input: String,
        enableTypoCorrection: Boolean,
        enablePrediction: Boolean,
        dictionary: CompactEnglishDictionary,
    ): List<Candidate> {
        val defaultType = CANDIDATE_TYPE_ENGLISH
        val lowerInput = input.lowercase()
        val limit = if (input.length <= 2) 6 else 12
        val singleWordOnly = input.none(Char::isWhitespace)

        val predictiveSearchReading = if (enablePrediction) {
            dictionary.readingTrie.predictiveSearch(
                prefix = lowerInput,
                succinctBitVector = dictionary.readingLbsIndex,
                limit = limit,
                singleWordOnly = singleWordOnly,
            )
        } else {
            emptyList()
        }

        // ★ typo はフラグが true のときだけ
        val typoCorrection = if (enableTypoCorrection && input.length > 2) {
            dictionary.readingTrie.commonPrefixSearchWithOmission(
                str = lowerInput,
                succinctBitVector = dictionary.readingLbsIndex
            ).filter { it.yomi.length == lowerInput.length }
        } else {
            emptyList()
        }

        Timber.d(
            "getCandidates English: [$input] predictive=$predictiveSearchReading typoEnabled=$enableTypoCorrection typo=[${typoCorrection.map { it.yomi }}]"
        )

        val predictions = mutableListOf<Candidate>()

        // input 自体の3種は常に入れる
        predictions += Candidate(
            string = input,
            score = 500,
            type = defaultType,
            length = input.length.toUByte()
        )
        predictions += Candidate(
            string = input.replaceFirstChar { it.uppercaseChar() },
            score = if (input.length <= 3) 9000 else if (input.length <= 4) 12000 else 57000,
            type = defaultType,
            length = input.length.toUByte()
        )
        predictions += Candidate(
            string = input.uppercase(),
            score = if (input.length <= 3) 9001 else if (input.length <= 4) 22001 else 57001,
            type = defaultType,
            length = input.length.toUByte()
        )

        // ★ fallback は predictive も (typo有効時のtypoも) 空のときだけ
        if (predictiveSearchReading.isEmpty() && typoCorrection.isEmpty()) {
            return listOf(
                Candidate(
                    string = input,
                    type = defaultType,
                    length = input.length.toUByte(),
                    score = 10000
                ),
                Candidate(
                    string = input.replaceFirstChar { it.uppercaseChar() },
                    type = defaultType,
                    length = input.length.toUByte(),
                    score = if (input.first().isUpperCase()) 8500 else 10001
                ),
                Candidate(
                    string = input.uppercase(),
                    type = defaultType,
                    length = input.length.toUByte(),
                    score = 10002
                )
            ).sortedBy { it.score }
        }
        // ============
        // 1) predictive（通常）
        // ============
        for (readingStr in predictiveSearchReading) {
            val nodeIndex = dictionary.readingTrie.getNodeIndex(
                readingStr,
                succinctBitVector = dictionary.readingLbsIndex
            )
            if (nodeIndex <= 0) continue

            val termId = dictionary.readingTrie.getTermId(
                nodeIndex,
                succinctBitVector = dictionary.readingLeafIndex
            )
            if (termId < 0) continue

            val listToken =
                dictionary.tokenArray.getListDictionaryByYomiTermId(termId, dictionary.tokenIndex)

            val variants = listToken.flatMap { entry ->
                val base = when (entry.nodeId) {
                    -1 -> readingStr
                    else -> dictionary.wordTrie.getLetter(
                        entry.nodeId,
                        succinctBitVector = dictionary.wordLbsIndex
                    )
                }

                if (singleWordOnly && !matchesSingleWordPrediction(lowerInput, base)) {
                    return@flatMap emptyList<Candidate>()
                }
                candidateCaseVariants(input, base).map { (surface, casePenalty) ->
                    Candidate(
                        string = surface,
                        type = defaultType,
                        length = surface.length.toUByte(),
                        score = entry.wordCost.toInt() + casePenalty
                    )
                }
            }

            predictions += variants
        }

        // ============
        // 2) typo（補正） - enableTypoCorrection のときだけ
        // ============
        if (enableTypoCorrection && typoCorrection.isNotEmpty()) {
            val typoType = 35.toByte()
            val predictiveSet = predictiveSearchReading.toHashSet()

            val maxEdits = maxEditsByLength(lowerInput.length)

            for (typo in typoCorrection) {
                val readingStr = typo.yomi
                if (!predictiveSet.add(readingStr)) continue

                val nodeIndex = dictionary.readingTrie.getNodeIndex(
                    readingStr,
                    succinctBitVector = dictionary.readingLbsIndex
                )
                if (nodeIndex <= 0) continue

                val termId = dictionary.readingTrie.getTermId(
                    nodeIndex,
                    succinctBitVector = dictionary.readingLeafIndex
                )
                if (termId < 0) continue

                val listToken =
                    dictionary.tokenArray.getListDictionaryByYomiTermId(termId, dictionary.tokenIndex)

                val penalty = if (typo.omissionOccurred) 9000 else 8000

                val variants = listToken.flatMap { entry ->
                    val base = when (entry.nodeId) {
                        -1 -> readingStr
                        else -> dictionary.wordTrie.getLetter(
                            entry.nodeId,
                            succinctBitVector = dictionary.wordLbsIndex
                        )
                    }

                    // ★ここで「input と似ているものだけ」に絞る（typo由来のみ）
                    val baseLower = base.lowercase()
                    if ((singleWordOnly && !matchesSingleWordPrediction(lowerInput, base)) ||
                        !withinEditDistance(baseLower, lowerInput, maxEdits)
                    ) {
                        return@flatMap emptyList<Candidate>()
                    }

                    candidateCaseVariants(input, base).map { (surface, casePenalty) ->
                        Candidate(
                            surface,
                            typoType,
                            surface.length.toUByte(),
                            entry.wordCost.toInt() + casePenalty + penalty
                        )
                    }
                }

                predictions += variants
            }
        }

        // 同一文字列は最小スコアのみ残す
        val deduped = predictions
            .groupBy { it.string }
            .map { (_, list) -> list.minBy { it.score } }

        return deduped.sortedBy { it.score }
    }

    // The phrase converter handles multiword predictions. A legacy dictionary
    // can still offer a spaced spelling for a fully typed compact alias.
    private fun matchesSingleWordPrediction(input: String, surface: String): Boolean =
        surface.none(Char::isWhitespace) || surface.filterNot(Char::isWhitespace).equals(input, ignoreCase = true)

    private fun withinEditDistance(a: String, b: String, maxEdits: Int): Boolean {
        val la = a.length
        val lb = b.length
        if (kotlin.math.abs(la - lb) > maxEdits) return false
        if (maxEdits == 0) return a == b
        if (a == b) return true

        // DP 1行で、maxEdits 超えたら早期終了
        var prev = IntArray(lb + 1) { it }
        var curr = IntArray(lb + 1)

        for (i in 1..la) {
            curr[0] = i
            var rowMin = curr[0]
            val ca = a[i - 1]
            for (j in 1..lb) {
                val cost = if (ca == b[j - 1]) 0 else 1
                val v = minOf(
                    prev[j] + 1,        // delete
                    curr[j - 1] + 1,    // insert
                    prev[j - 1] + cost  // replace
                )
                curr[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > maxEdits) return false
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[lb] <= maxEdits
    }

    private fun ensureDictionariesLoaded(): EnglishDictionaryData {
        dictionaryData?.let { return it }
        return synchronized(this) {
            dictionaryData ?: checkNotNull(dictionaryLoader) {
                "English dictionary loader is not configured"
            }.invoke().also {
                dictionaryData = it
                dictionaryLoader = null
            }
        }
    }

    private fun loadDictionaryData(reader: DictionaryBinaryReader): EnglishDictionaryData {
        reader.loadBundledEnglishForEngine()?.let { bundled ->
            return EnglishDictionaryData(bundled, reader::loadBundledEnglishGrammar)
        }
        val reading = reader.loadEnglishReading(DictionaryFileKey.ENGLISH_READING)
        val word = reader.loadEnglishWord(DictionaryFileKey.ENGLISH_WORD)
        val token = reader.loadEnglishToken(DictionaryFileKey.ENGLISH_TOKEN)
        return EnglishDictionaryData(
            CompactEnglishDictionary(
                reading, word, token,
                reader.loadEnglishReadingLbsIndex(reading),
                reader.loadEnglishReadingLeafIndex(reading),
                reader.loadEnglishWordLbsIndex(word),
                reader.loadEnglishTokenIndex(token),
            ),
        )
    }

    private class EnglishDictionaryData(
        val dictionary: CompactEnglishDictionary,
        loadGrammar: (() -> GrammarRoles?)? = null,
    ) {
        val phraseConverter by lazy {
            loadGrammar?.invoke()?.let {
                // Candidate generation runs on every keystroke. Keep the interactive
                // search narrower than the standalone reference converter.
                PhraseConverter(EnglishPhraseDictionary(dictionary, it), beamWidth = 24, completionLimit = 32)
            }
        }
    }

    private fun maxEditsByLength(len: Int): Int = when {
        len <= 3 -> 1
        len <= 6 -> 1
        len <= 10 -> 2
        else -> 3
    }

}

private fun MutableMap<String, QwertyGlideDictionaryEntry>.mergeGlideEntry(
    word: String,
    wordCost: Int
) {
    val normalizedWord = word.lowercase()
    val current = this[normalizedWord]
    if (current == null || wordCost < current.wordCost) {
        this[normalizedWord] = QwertyGlideDictionaryEntry(normalizedWord, wordCost)
    }
}
