package com.kazumaproject.markdownhelperkeyboard.dictionary_override

import com.google.gson.JsonParser
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import org.tukaani.xz.XZInputStream

/** Private, reproducible dictionary copies used by the existing memory-mapped readers. */
internal class MappedDictionaryAssetCache(
    private val directory: File,
    manifest: String,
    private val openAsset: (String) -> InputStream,
) {
    private enum class Compression(val extension: String) {
        XZ("xz"), ZSTANDARD("zst")
    }

    private data class Asset(val size: Long, val sha256: String, val compression: Compression)

    private val assets: Map<String, Asset> = JsonParser.parseString(manifest).asJsonObject.let { root ->
        require(root.get("version").asInt == 1) { "Unsupported dictionary asset manifest" }
        root.getAsJsonObject("assets").entrySet().associate { (path, value) ->
            val record = value.asJsonObject
            val compression = when (val encoding = record.get("compression")?.asString) {
                null, "xz" -> Compression.XZ
                "zstd" -> Compression.ZSTANDARD
                else -> error("Unsupported dictionary compression: $encoding")
            }
            val asset = Asset(record.get("size").asLong, record.get("sha256").asString, compression)
            require(asset.size in 1..MAX_ASSET_BYTES && SHA256.matches(asset.sha256)) {
                "Invalid dictionary asset record: $path"
            }
            path to asset
        }.also { require(it.isNotEmpty()) { "Empty dictionary asset manifest" } }
    }

    fun contains(path: String): Boolean = path in assets

    fun fileFor(path: String): File {
        val english = path.startsWith("english/")
        val processLock = if (english) englishProcessLock else coreProcessLock
        val lockName = if (english) ".lock" else ".core.lock"
        val temporaryPrefix = if (english) "dictionary-" else "core-dictionary-"
        val asset = assets.getValue(path)
        return synchronized(processLock) {
            if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
                throw IOException("Cannot create private dictionary directory")
            }
            // Keep English preparation independent of the core dictionary load.
            RandomAccessFile(File(directory, lockName), "rw").channel.use { channel ->
                channel.lock().use {
                    val liveFiles = assets.values.mapTo(HashSet()) { it.sha256 }
                    directory.listFiles()?.filter {
                        // The other cache writer can still be using its own temporary file.
                        (it.name.startsWith(temporaryPrefix) && it.name.endsWith(".tmp")) ||
                            (SHA256.matches(it.name) && it.name !in liveFiles)
                    }?.forEach { it.delete() }
                    val destination = File(directory, asset.sha256)
                    if (destination.isFile && destination.length() == asset.size) return@synchronized destination
                    val temporary = File.createTempFile(temporaryPrefix, ".tmp", directory)
                    try {
                        FileOutputStream(temporary).use { output ->
                            copyVerified(path) { bytes, count -> output.write(bytes, 0, count) }
                            output.fd.sync()
                        }
                        if (!temporary.setReadOnly() || !temporary.renameTo(destination)) {
                            throw IOException("Cannot publish private dictionary: $path")
                        }
                        destination
                    } finally {
                        temporary.delete()
                    }
                }
            }
        }
    }

    /** A full/read-only filesystem must not make the bundled dictionary unavailable. */
    fun readBytes(path: String): ByteArray {
        val bytes = ByteArray(assets.getValue(path).size.toInt())
        var offset = 0
        copyVerified(path) { buffer, count ->
            buffer.copyInto(bytes, offset, 0, count)
            offset += count
        }
        return bytes
    }

    private fun copyVerified(path: String, consume: (ByteArray, Int) -> Unit) {
        val asset = assets.getValue(path)
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        val extension = asset.compression.extension
        openAsset("$path.$extension").use { raw ->
            val decoded = when (asset.compression) {
                // The build uses an 8 MiB XZ dictionary. Also bound memory on damaged streams.
                Compression.XZ -> XZInputStream(raw, 32 * 1024)
                Compression.ZSTANDARD -> openZstandardDictionary(raw)
            }
            decoded.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    size += count
                    if (size > asset.size) throw IOException("Dictionary exceeds expected size: $path")
                    digest.update(buffer, 0, count)
                    consume(buffer, count)
                }
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (size != asset.size || hash != asset.sha256) {
            throw IOException("Dictionary checksum/size mismatch: $path")
        }
    }

    companion object {
        private val englishProcessLock = Any()
        private val coreProcessLock = Any()
        private val SHA256 = Regex("[0-9a-f]{64}")
        private const val MAX_ASSET_BYTES = 128L * 1024 * 1024
    }
}
