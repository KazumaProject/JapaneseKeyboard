package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.connection_id.ConnectionIdBuilder
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.TripleDictionaryData
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcNodeAttributeTableReader
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcSegmenter
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcSegmenterDataReader
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.CompositeSystemNgramDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.EmptySystemNgramDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.PackedSystemNgramDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.FindPath
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.model.LearnResult
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.util.zip.ZipInputStream

class LearnedConversionRegressionTest {
    @Test
    fun zeroScoreLearnedNamesRemainStrongAloneWithoutDisplacingOrdinaryCompounds() = runBlocking {
        val assets = RealDictionaryFixture()
        val cases = listOf(
            RegressionCase("けい", "慧", 1922, listOf("けいさん", "けいかいたいせい", "かんけいない", "けいちょう")),
            RegressionCase("ちょう", "徴", 1841, listOf("ちょうし", "けんちょう", "かちょうふうげつ", "ちょうかい", "けいちょう")),
            RegressionCase("ちょう", "徴", 1923, listOf("ちょうし", "けんちょう", "かちょうふうげつ", "ちょうかい", "けいちょう")),
        )
        val expectedOrdinaryCandidates = mapOf(
            "けいさん" to listOf("計算", "経産"),
            "けいかいたいせい" to listOf("警戒態勢"),
            "かんけいない" to listOf("関係ない", "関係無い"),
            "けいちょう" to listOf("傾聴", "慶長", "慶弔", "経腸"),
            "ちょうし" to listOf("調子", "銚子", "長子"),
            "けんちょう" to listOf("県庁", "堅調", "建長", "健聴"),
            "かちょうふうげつ" to listOf("花鳥風月"),
            "ちょうかい" to listOf("懲戒", "町会", "朝会"),
        )
        val modes = listOf(false, true).flatMap { ngram ->
            listOf(false, true).map { bunsetsu -> ngram to bunsetsu }
        }

        for ((reading, surface, leftId, expressions) in cases) {
            val history = listOf(
                LearnEntity(
                    input = reading,
                    out = surface,
                    score = 0,
                    leftId = leftId.toShort(),
                    rightId = leftId.toShort(),
                    usageCount = 7,
                    lastUsedAt = 123456L,
                    isPhrase = false,
                    id = 17,
                ),
            )
            val dao = InMemoryReadOnlyLearnDao(history)
            val repository = LearnRepository(dao)
            val revision = repository.conversionRevision

            for ((ngramEnabled, bunsetsuEnabled) in modes) {
                val exactName = assets.convert(
                    input = reading,
                    learnRepository = repository,
                    ngramEnabled = ngramEnabled,
                    bunsetsuEnabled = bunsetsuEnabled,
                ).first().string
                assertEquals("Exact $reading → $surface (ngram=$ngramEnabled, bunsetsu=$bunsetsuEnabled)", surface, exactName)

                for (expression in expressions) {
                    val before = assets.convert(
                        input = expression,
                        learnRepository = null,
                        ngramEnabled = ngramEnabled,
                        bunsetsuEnabled = bunsetsuEnabled,
                    )
                    val after = assets.convert(
                        input = expression,
                        learnRepository = repository,
                        ngramEnabled = ngramEnabled,
                        bunsetsuEnabled = bunsetsuEnabled,
                    )
                    val ordinaryTop4 = expectedOrdinaryCandidates.getValue(expression)
                        .filter { candidate -> before.take(4).any { it.string == candidate } }
                    val afterTop4 = after.take(4).map(Candidate::string)

                    assertTrue(
                        "Learned $reading → $surface displaced baseline top for '$expression' " +
                            "(ngram=$ngramEnabled, bunsetsu=$bunsetsuEnabled): " +
                            "before=${before.take(4).map(Candidate::string)}, after=$afterTop4",
                        afterTop4.firstOrNull() == before.firstOrNull()?.string,
                    )
                    for (ordinaryCandidate in ordinaryTop4) {
                        assertTrue(
                            "Expected ordinary candidate '$ordinaryCandidate' in top 4 for '$expression' " +
                                "(ngram=$ngramEnabled, bunsetsu=$bunsetsuEnabled): $afterTop4",
                            ordinaryCandidate in afterTop4,
                        )
                    }
                }
            }

            assertEquals("Conversion must not mutate existing learned history", history, dao.getAllSuspend())
            assertEquals("Conversion queries must not write to the repository", revision, repository.conversionRevision)
        }
    }

    @Test
    fun learnedOnlyNamesKeepExactPreferenceAndPreserveHistory() = runBlocking {
        val assets = RealDictionaryFixture()
        val history = listOf(
            LearnEntity("けい", "慧人", score = 0, leftId = 1851, rightId = 1851, id = 42),
        )
        val repository = LearnRepository(InMemoryReadOnlyLearnDao(history))
        for (ngram in listOf(false, true)) for (bunsetsu in listOf(false, true)) {
            assertEquals("慧人", assets.convert("けい", repository, ngram, bunsetsu).first().string)
            assertEquals("計算", assets.convert("けいさん", repository, ngram, bunsetsu).first().string)
        }
        assertEquals(history, repository.allSuspend())
    }

    @Test
    fun cachedAppendDeletionAndCatchUpMatchFreshGraphsForLegacyAndSessionCaches() = runBlocking {
        val assets = RealDictionaryFixture()
        val history = listOf(
            LearnEntity(input = "けい", out = "慧", score = 0, leftId = 1922, rightId = 1922, id = 1),
            LearnEntity(input = "ちょう", out = "徴", score = 0, leftId = 1841, rightId = 1841, id = 2),
        )
        val repository = LearnRepository(InMemoryReadOnlyLearnDao(history))
        val sequences = listOf(
            listOf("け", "けい", "けいさ", "けいさん", "けいさ", "けい", "けいさん"),
            listOf("ち", "ちょ", "ちょう", "ちょうし"),
            listOf("かん", "かんけい", "かんけいない"),
        )

        for (ngram in listOf(false, true)) for (bunsetsu in listOf(false, true)) for (sequence in sequences) {
            for (sessionCache in listOf(false, true)) {
                val reusedBuilder = GraphBuilder()
                val reusedState = if (sessionCache) reusedBuilder.createSessionState() else null
                val reusedFinder = assets.createFinder(ngram, bunsetsu)
                val reusedPathState = if (sessionCache) reusedFinder.createSessionState() else null

                for (input in sequence) {
                    val cached = assets.convert(
                        input = input,
                        learnRepository = repository,
                        ngramEnabled = ngram,
                        bunsetsuEnabled = bunsetsu,
                        graphBuilder = reusedBuilder,
                        graphSessionState = reusedState,
                        findPath = reusedFinder,
                        pathSessionState = reusedPathState,
                    ).fingerprint()
                    val fresh = assets.convert(
                        input = input,
                        learnRepository = repository,
                        ngramEnabled = ngram,
                        bunsetsuEnabled = bunsetsu,
                    ).fingerprint()

                    assertEquals(
                        "Cached/fresh mismatch for '$input' (sessionCache=$sessionCache, ngram=$ngram, bunsetsu=$bunsetsu)",
                        fresh,
                        cached,
                    )
                }
            }
        }
        assertEquals("Conversion must preserve both learned records", history, repository.allSuspend())
    }

    private fun List<Candidate>.fingerprint(): List<List<Any?>> = map {
        listOf(it.string, it.score, it.type, it.length, it.yomi, it.leftId, it.rightId)
    }

    private data class RegressionCase(
        val reading: String,
        val surface: String,
        val leftId: Int,
        val expressions: List<String>,
    )

    private class RealDictionaryFixture {
        private val system = readTriple("system", zipped = true)
        private val connectionMatrix = asset("connectionId.dat.zip") {
            ConnectionMatrix.fromShortArray(ConnectionIdBuilder().readShortArrayFromBytes(it))
        }
        private val segmenter = asset("mozc/segmenter.dat") {
            MozcSegmenter(MozcSegmenterDataReader().read(it))
        }
        private val attributes = asset("mozc/node_attribute_by_lid.dat") {
            MozcNodeAttributeTableReader().read(it)
        }
        private val ngram = CompositeSystemNgramDictionary(
            listOf(
                PackedSystemNgramDictionary.read(File(assetDirectory, "ngram/system_ngram.dat").readBytes()),
                PackedSystemNgramDictionary.read(File(assetDirectory, "ngram/system_ngram_unigram.dat").readBytes()),
                PackedSystemNgramDictionary.readStandalone(
                    File(assetDirectory, "ngram/system_ngram_standalone_unigram.dat").readBytes(),
                ),
            ),
        )

        fun createFinder(ngramEnabled: Boolean, bunsetsuEnabled: Boolean) = FindPath(
            systemNgramDictionaryProvider = { if (ngramEnabled) ngram else EmptySystemNgramDictionary },
            mozcSegmenterProvider = { if (bunsetsuEnabled) segmenter else null },
        )

        suspend fun convert(
            input: String,
            learnRepository: LearnRepository?,
            ngramEnabled: Boolean,
            bunsetsuEnabled: Boolean,
            graphBuilder: GraphBuilder = GraphBuilder(),
            graphSessionState: GraphBuilder.SessionState? = null,
            findPath: FindPath? = null,
            pathSessionState: FindPath.SessionState? = null,
        ): List<Candidate> {
            val graph = graphBuilder.constructGraph(
                str = input,
                yomiTrie = system.yomiTrie,
                tangoTrie = system.tangoTrie,
                tokenArray = system.tokenArray,
                succinctBitVectorLBSYomi = system.succinctBitVectorLBSYomi,
                succinctBitVectorIsLeafYomi = system.succinctBitVectorIsLeafYomi,
                succinctBitVectorTokenArray = system.succinctBitVectorTokenArray,
                succinctBitVectorTangoLBS = system.succinctBitVectorTangoLBS,
                userDictionaryRepository = null,
                learnRepository = learnRepository,
                isOmissionSearchEnable = false,
                typoCorrectionOffsetScore = 3000,
                omissionSearchOffSetScore = 1900,
                mozcNodeAttributeTable = attributes,
                graphNodeTrace = null,
                sessionState = graphSessionState,
            )
            val activeFinder = findPath ?: createFinder(ngramEnabled, bunsetsuEnabled)

            if (bunsetsuEnabled) {
                val result = activeFinder.backwardAStarWithBunsetsu(
                    graph = graph,
                    length = input.length,
                    connectionMatrix = connectionMatrix,
                    n = 4,
                    beamWidth = 20,
                    sessionState = pathSessionState,
                    originalInput = input,
                )
                return result.candidates
            }
            return activeFinder.backwardAStar(
                graph = graph,
                length = input.length,
                connectionMatrix = connectionMatrix,
                n = 4,
                beamWidth = 20,
                sessionState = pathSessionState,
                originalInput = input,
            )
        }

        private fun readTriple(directory: String, zipped: Boolean): TripleDictionaryData {
            val suffix = if (zipped) ".dat.zip" else ".dat"
            val tango = readObject("$directory/tango$suffix") { LOUDS().readExternalNotCompress(it) }
            val yomi = readObject("$directory/yomi$suffix") { LOUDSWithTermId().readExternalNotCompress(it) }
            val token = readObject("$directory/token$suffix") { stream ->
                TokenArray().also { it.readExternal(stream) }
            }
            readObject("pos_table.dat") { token.readPOSTable(it) }
            return TripleDictionaryData(
                tangoTrie = tango,
                yomiTrie = yomi,
                tokenArray = token,
                succinctBitVectorLBSYomi = SuccinctBitVector(yomi.LBS),
                succinctBitVectorIsLeafYomi = SuccinctBitVector(yomi.isLeaf),
                succinctBitVectorTokenArray = SuccinctBitVector(token.bitvector),
                succinctBitVectorTangoLBS = SuccinctBitVector(tango.LBS),
            )
        }

        private fun <T> readObject(path: String, block: (ObjectInputStream) -> T): T =
            asset(path) { ObjectInputStream(BufferedInputStream(it)).use(block) }
    }

    internal class InMemoryReadOnlyLearnDao(entries: List<LearnEntity>) : LearnDao {
        private val rows = entries.toList()

        override suspend fun findByInputPrefix(prefix: String, prefixUpperBound: String): List<LearnEntity> =
            rows.filter { it.input >= prefix && it.input < prefixUpperBound }

        override suspend fun getAllSuspend(): List<LearnEntity> = rows.toList()
        override fun all(): Flow<List<LearnEntity>> = flowOf(rows.toList())

        override suspend fun editEntry(id: Int, input: String, output: String, score: Int): Int = error("unused")
        override suspend fun deleteById(id: Int): Unit = error("unused")
        override suspend fun insertStrict(entry: LearnEntity): Unit = error("unused")
        override suspend fun insert(learnData: LearnEntity): Unit = error("unused")
        override suspend fun findByInput(input: String): List<LearnResult>? = error("unused")
        override suspend fun findByInputAndOutput(input: String, output: String): LearnEntity? = error("unused")
        override suspend fun existsDuplicateForUpdate(input: String, output: String, excludeId: Int): Boolean = error("unused")
        override suspend fun insertAll(learnDataList: List<LearnEntity>): Unit = error("unused")
        override suspend fun predictiveSearchByInput(prefix: String, prefixUpperBound: String, limit: Int): List<LearnEntity> = error("unused")
        override suspend fun findCommonPrefixes(searchTerm: String): List<LearnEntity> = error("unused")
        override suspend fun updateLearnedData(learnData: LearnEntity): Unit = error("unused")
        override suspend fun delete(learnData: LearnEntity): Unit = error("unused")
        override suspend fun deleteAll(): Unit = error("unused")
        override suspend fun deleteByInput(input: String): Int = error("unused")
        override suspend fun deleteByInputAndOutput(input: String, output: String): Int = error("unused")
    }

    companion object {
        private val assetDirectory: File by lazy {
            listOf(File("app/src/main/assets"), File("src/main/assets"))
                .firstOrNull(File::exists)
                ?: error("Cannot find app/src/main/assets from ${File(".").absolutePath}")
        }

        private fun <T> asset(path: String, block: (InputStream) -> T): T {
            val buffered = BufferedInputStream(FileInputStream(File(assetDirectory, path)))
            val stream: InputStream = if (path.endsWith(".zip")) {
                ZipInputStream(buffered).also { checkNotNull(it.nextEntry) { "Empty asset archive: $path" } }
            } else {
                buffered
            }
            return stream.use(block)
        }
    }
}
