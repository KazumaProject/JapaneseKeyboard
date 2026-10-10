package com.kazumaproject.counter

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.util.zip.CRC32

internal data class EndingRecord(val unit: Int, val restored: String, val terminal: Int)

/** JKCR v1, with an optional precompiled output-ending index. */
class CounterDictionary private constructor(
    internal val numbers: List<NumberPart>, internal val units: List<CounterUnit>,
    internal val endings: List<EndingRecord>, internal val exceptions: List<CounterException>,
    internal val surfaces: List<CounterSurface>, internal val numberTrie: CounterTrie,
    internal val endingTrie: CounterTrie, internal val exceptionTrie: CounterTrie, val byteSize: Int,
    private val surfaceEndBits: LongArray?,
) {
    val counterCount: Int get() = units.size
    val numberPartCount: Int get() = numbers.size
    val endingCount: Int get() = endings.size
    val exceptionCount: Int get() = exceptions.size
    val aliasCount: Int get() = surfaces.size
    val stateCount: Int get() = numberTrie.edges.size + endingTrie.edges.size + exceptionTrie.edges.size - 3
    val primitiveIndexBytes: Int get() = listOf(numberTrie, endingTrie, exceptionTrie).sumOf {
        (it.edges.size + it.targets.size + it.postings.size + it.outputs.size) * 4 + it.labels.size * 2
    }
    fun converter(): CounterConverter = CounterConverter(this)

    internal fun mayEndQuantitySurface(surface: String): Boolean {
        if (surface.isEmpty()) return false
        val bits = surfaceEndBits ?: return true // Legacy dictionaries conservatively use detailed parsing.
        val code = surface[surface.lastIndex].code
        return bits[code ushr 6] and (1L shl (code and 63)) != 0L
    }

    companion object {
        const val FORMAT_VERSION = 1
        const val MAX_BYTES = 128 * 1024
        private const val MAGIC = 0x52434b4a // bytes: JKCR
        private const val HEADER_SIZE = 16
        private const val SURFACE_END_WORDS = 1024
        private const val SURFACE_END_BYTES = 4 + SURFACE_END_WORDS * 8

        internal fun compile(source: CounterSource): ByteArray {
            val pool = (source.numbers.map { it.reading } + source.units.flatMap { listOf(it.id, it.surface, it.category) } +
                source.endings.map { it.restored } + source.exceptions.flatMap { listOf(it.reading, it.suffix) } + source.surfaces.map { it.surface }).distinct().sorted()
            val ids = pool.withIndex().associate { it.value to it.index }
            val output = ByteArrayOutputStream()
            fun int(value: Int) { repeat(4) { output.write(value ushr (it * 8)) } }
            fun long(value: Long) { repeat(8) { output.write((value ushr (it * 8)).toInt()) } }
            fun string(value: String) = int(ids.getValue(value))
            fun ints(values: IntArray) { int(values.size); values.forEach { int(it) } }
            fun trie(value: CounterTrie) {
                ints(value.edges); int(value.labels.size)
                value.labels.forEach { output.write(it.code); output.write(it.code ushr 8) }
                ints(value.targets); ints(value.postings); ints(value.outputs)
            }
            int(pool.size)
            for (entry in pool) { val utf8 = entry.toByteArray(Charsets.UTF_8); int(utf8.size); output.write(utf8) }
            int(source.numbers.size)
            source.numbers.forEach { string(it.reading); long(it.value); int(it.kind); long(it.place) }
            int(source.units.size)
            source.units.forEach { string(it.id); string(it.surface); string(it.category); int(it.priority); long(it.min); long(it.max); int(it.blocked) }
            int(source.endings.size)
            source.endings.forEach { int(it.unit); string(it.restored); int(it.terminal) }
            int(source.exceptions.size)
            source.exceptions.forEach { int(it.unit); long(it.number); string(it.reading); int(if (it.replace) 1 else 0); string(it.suffix) }
            int(source.surfaces.size)
            source.surfaces.forEach { int(it.unit); string(it.surface); int(it.priority) }
            trie(CounterTrie.build(source.numbers.mapIndexed { i, part -> part.reading to i }))
            trie(CounterTrie.build(source.endings.mapIndexed { i, ending -> ending.reading to i }, reversed = true))
            trie(CounterTrie.build(source.exceptions.mapIndexed { i, exception -> exception.reading to i }))
            val payload = output.toByteArray()
            require(payload.size + HEADER_SIZE <= MAX_BYTES) { "Counter dictionary exceeds 128 KiB: ${payload.size + HEADER_SIZE}" }
            val crc = CRC32().apply { update(payload) }.value.toInt()
            return ByteBuffer.allocate(HEADER_SIZE + payload.size).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(MAGIC).putInt(FORMAT_VERSION).putInt(payload.size).putInt(crc).put(payload).array()
        }

        fun read(input: InputStream): CounterDictionary {
            val output = ByteArrayOutputStream(); val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                require(count <= MAX_BYTES - output.size()) { "Counter dictionary exceeds size limit" }
                output.write(buffer, 0, count)
            }
            return read(output.toByteArray())
        }

        fun read(bytes: ByteArray): CounterDictionary {
            require(bytes.size in HEADER_SIZE..MAX_BYTES) { "Invalid counter dictionary size" }
            val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(data.int == MAGIC) { "Invalid counter dictionary magic" }
            require(data.int == FORMAT_VERSION) { "Unsupported counter dictionary version" }
            require(data.int == bytes.size - HEADER_SIZE) { "Invalid payload length" }
            val crc = data.int
            require(CRC32().apply { update(bytes, HEADER_SIZE, bytes.size - HEADER_SIZE) }.value.toInt() == crc) { "Counter dictionary checksum mismatch" }
            fun int(): Int { require(data.remaining() >= 4) { "Truncated counter dictionary" }; return data.int }
            fun long(): Long { require(data.remaining() >= 8) { "Truncated counter dictionary" }; return data.long }
            fun count(bytesPerItem: Int = 4): Int = int().also { require(it >= 0 && it <= data.remaining() / bytesPerItem) { "Invalid section count" } }
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            val pool = List(count()) {
                val size = count(1); val value = ByteArray(size); data.get(value)
                decoder.reset().decode(ByteBuffer.wrap(value)).toString()
            }
            fun string(): String { val id = int(); require(id in pool.indices) { "Invalid string ID" }; return pool[id] }
            val numbers = List(count(24)) { NumberPart(string(), long(), int(), long()) }
            val units = List(count(36)) { CounterUnit(string(), string(), string(), int(), long(), long(), int()) }
            val endings = List(count(12)) { EndingRecord(int(), string(), int()) }
            val exceptions = List(count(24)) {
                val unit = int(); val number = long(); val reading = string(); val mode = int(); val suffix = string()
                require(mode in 0..1); CounterException(unit, number, reading, mode == 1, suffix)
            }
            val surfaces = List(count(12)) { CounterSurface(int(), string(), int()) }
            fun ints(): IntArray = IntArray(count()) { int() }
            fun trie(): CounterTrie {
                val edges = ints(); val labels = CharArray(count(2)) { data.char }
                return CounterTrie(edges, labels, ints(), ints(), ints())
            }
            val numberTrie = trie(); val endingTrie = trie(); val exceptionTrie = trie()
            val surfaceEndBits = when (data.remaining()) {
                0 -> null
                SURFACE_END_BYTES -> {
                    require(int() == SURFACE_END_WORDS) { "Invalid surface ending index count" }
                    LongArray(SURFACE_END_WORDS).also { bits ->
                        data.asLongBuffer().get(bits)
                        data.position(data.position() + SURFACE_END_WORDS * 8)
                    }
                }
                else -> throw IllegalArgumentException("Invalid surface ending index length")
            }
            require(numbers.isNotEmpty() && numbers.all(::validNumber) && numbers.map { it.reading }.distinct().size == numbers.size)
            require(units.isNotEmpty() && units.map { it.id }.distinct().size == units.size)
            units.forEach { require(it.id.isNotEmpty() && it.surface.isNotEmpty() && it.min >= 0 && it.max >= it.min && it.priority >= 0) }
            endings.forEach { require(it.unit in units.indices && (it.terminal == -1 || it.terminal in 0..9 || it.terminal in listOf(10, 100, 1000, 10000))) }
            exceptions.forEach { require(it.unit in units.indices && it.number in units[it.unit].min..units[it.unit].max && it.reading.isNotEmpty()) }
            require(exceptions.zipWithNext().all { (a, b) -> a.unit < b.unit || (a.unit == b.unit && a.number <= b.number) }) { "Unsorted exceptions" }
            surfaces.forEach { require(it.unit in units.indices && it.surface.isNotEmpty() && it.priority > 0) }
            numberTrie.verify(numbers.size); endingTrie.verify(endings.size); exceptionTrie.verify(exceptions.size)
            require(numberTrie.outputs.size == numbers.size && numberTrie.outputs.distinct().size == numbers.size)
            for ((i, part) in numbers.withIndex()) {
                val node = numberTrie.exact(part.reading)
                require(node >= 0 && numberTrie.postings[node + 1] - numberTrie.postings[node] == 1 && numberTrie.outputs[numberTrie.postings[node]] == i) { "Mismatched number index" }
            }
            return CounterDictionary(numbers, units, endings, exceptions, surfaces, numberTrie, endingTrie, exceptionTrie, bytes.size, surfaceEndBits)
        }
    }
}
