package com.kazumaproject.markdownhelperkeyboard.local_font

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class LocalFontState(
    val snapshot: KeyboardFontSnapshot = KeyboardFontSnapshot(),
    val displayName: String? = null,
    val warning: LocalFontWarning? = null,
)

enum class LocalFontWarning { RESTORE_FAILED }

data class LocalFontPreview internal constructor(
    internal val generation: Long,
    internal val record: LocalFontRecord,
    val typeface: Typeface,
    val displayName: String,
)

@Singleton
class LocalFontRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val resolver: ContentResolver = context.contentResolver
    private val store = LocalFontStore(context.noBackupFilesDir)
    private val operationMutex = Mutex()
    private val generation = AtomicLong(0L)
    private val generationGate = Any()
    private var loaded = false
    private var activeRecord: LocalFontRecord? = null
    private var preview: LocalFontPreview? = null
    private val _state = MutableStateFlow(LocalFontState())
    val state: StateFlow<LocalFontState> = _state.asStateFlow()

    suspend fun loadIfNeeded(): LocalFontState = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            if (loaded) return@withLock _state.value
            loaded = true
            when (val readResult = store.readState()) {
                LocalFontReadResult.Missing -> {
                    publishStandardState()
                    store.collectGarbage(activeId = null, previewId = null)
                    _state.value
                }
                LocalFontReadResult.Failed -> {
                    // The saved selection may still be recoverable; keep every font copy intact.
                    publishStandardState(LocalFontWarning.RESTORE_FAILED)
                    _state.value
                }
                is LocalFontReadResult.Stored -> {
                    val record = readResult.state.record
                    if (record == null) {
                        publishStandardState()
                        store.collectGarbage(activeId = null, previewId = null)
                        _state.value
                    } else {
                        try {
                            val file = store.fileFor(record)
                            if (!file.isFile || file.length() != record.sizeBytes || sha256(file) != record.sha256) {
                                throw IOException("Saved font is unavailable")
                            }
                            SfntFontValidator.validate(file)
                            val typeface = createTypeface(file)
                            activeRecord = record
                            _state.value = LocalFontState(
                                snapshot = KeyboardFontSnapshot(typeface = typeface, revision = 1L),
                                displayName = record.displayName,
                            )
                            KeyboardFontApplicator.updateProcessSnapshot(_state.value.snapshot)
                        } catch (e: CancellationException) {
                            loaded = false
                            throw e
                        } catch (_: Exception) {
                            publishStandardState(LocalFontWarning.RESTORE_FAILED)
                            return@withLock _state.value
                        }
                        store.collectGarbage(activeRecord?.id, preview?.record?.id)
                        _state.value
                    }
                }
            }
        }
    }

    private fun publishStandardState(warning: LocalFontWarning? = null) {
        activeRecord = null
        _state.value = LocalFontState(
            snapshot = KeyboardFontSnapshot(revision = 1L),
            warning = warning,
        )
        KeyboardFontApplicator.updateProcessSnapshot(_state.value.snapshot)
    }

    suspend fun prepare(uri: Uri): LocalFontPreview = withContext(Dispatchers.IO) {
        val token = synchronized(generationGate) { generation.incrementAndGet() }
        operationMutex.withLock {
            val oldPreview = preview
            preview = null
            store.delete(oldPreview?.let { store.fileFor(it.record) })
            val temp = store.newStagingFile()
            var publishedFile: File? = null
            try {
                val displayName = queryDisplayName(uri)
                val copied = copyUriToFile(uri, temp, token)
                SfntFontValidator.validate(temp)
                val signature = readSignature(temp)
                val extension = if (signature == "OTTO") "otf" else "ttf"
                val record = store.newRecord(displayName, extension, copied.first, copied.second)
                checkCurrent(token)
                val file = store.publish(temp, record).also { publishedFile = it }
                val typeface = createTypeface(file)
                checkCurrent(token)
                val prepared = LocalFontPreview(token, record, typeface, displayName)
                preview = prepared
                store.collectGarbage(activeRecord?.id, record.id)
                publishedFile = null
                prepared
            } catch (e: CancellationException) {
                if (preview?.generation == token) preview = null
                store.delete(temp)
                store.delete(publishedFile)
                throw e
            } catch (e: Exception) {
                if (preview?.generation == token) preview = null
                store.delete(temp)
                store.delete(publishedFile)
                throw e
            }
        }
    }

    suspend fun apply(prepared: LocalFontPreview) = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val file = store.fileFor(prepared.record)
            if (preview !== prepared || generation.get() != prepared.generation) {
                throw CancellationException("Font selection was superseded")
            }
            if (!file.isFile || file.length() != prepared.record.sizeBytes || sha256(file) != prepared.record.sha256) {
                throw IOException("Prepared font is unavailable")
            }
            synchronized(generationGate) {
                checkCurrent(prepared.generation)
                store.writeState(prepared.record)
                activeRecord = prepared.record
                preview = null
                val revision = _state.value.snapshot.revision + 1L
                _state.value = LocalFontState(
                    snapshot = KeyboardFontSnapshot(prepared.typeface, revision),
                    displayName = prepared.displayName,
                )
                KeyboardFontApplicator.updateProcessSnapshot(_state.value.snapshot)
            }
            store.collectGarbage(activeRecord?.id, null)
        }
    }

    suspend fun cancelPreview(prepared: LocalFontPreview?) = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            if (prepared == null || preview === prepared) {
                if (prepared != null) {
                    synchronized(generationGate) {
                        if (generation.get() == prepared.generation) generation.incrementAndGet()
                    }
                    store.delete(store.fileFor(prepared.record))
                }
                preview = null
            }
            store.collectGarbage(activeRecord?.id, preview?.record?.id)
        }
    }

    suspend fun restoreStandard() = withContext(Dispatchers.IO) {
        synchronized(generationGate) { generation.incrementAndGet() }
        operationMutex.withLock {
            synchronized(generationGate) {
                store.writeState(null)
                activeRecord = null
                preview?.let { store.delete(store.fileFor(it.record)) }
                preview = null
                val revision = _state.value.snapshot.revision + 1L
                _state.value = LocalFontState(snapshot = KeyboardFontSnapshot(revision = revision))
                KeyboardFontApplicator.updateProcessSnapshot(_state.value.snapshot)
            }
            store.collectGarbage(activeId = null, previewId = null)
        }
    }

    private suspend fun queryDisplayName(uri: Uri): String {
        var cursor: Cursor? = null
        return try {
            cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            val name = if (cursor?.moveToFirst() == true) {
                cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 } ?: -1)
            } else null
            LocalFontStore.sanitizeDisplayName(name.orEmpty())
        } catch (_: Exception) {
            "Local font"
        } finally {
            cursor?.close()
        }
    }

    private suspend fun copyUriToFile(uri: Uri, target: File, token: Long): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        val signal = CancellationSignal()
        val descriptor = resolver.openFileDescriptor(uri, "r", signal)
            ?: throw IOException("Unable to open selected font")
        var total = 0L
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    checkCurrent(token)
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    total += count.toLong()
                    if (total > MAX_LOCAL_FONT_BYTES) throw FontValidationException(FontFormatIssue.TOO_LARGE)
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        if (total == 0L) throw FontValidationException(FontFormatIssue.EMPTY)
        return total to digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun createTypeface(file: File): Typeface {
        return try {
            if (Build.VERSION.SDK_INT >= 26) {
                Typeface.Builder(file).build() ?: throw IOException("Font could not be loaded")
            } else {
                Typeface.createFromFile(file)
            }
        } catch (e: IOException) {
            throw FontValidationException(FontFormatIssue.MALFORMED, e)
        } catch (e: RuntimeException) {
            throw FontValidationException(FontFormatIssue.MALFORMED, e)
        }
    }

    private fun readSignature(file: File): String =
        file.inputStream().use { input ->
            val bytes = ByteArray(4)
            if (input.read(bytes) != bytes.size) throw FontValidationException(FontFormatIssue.MALFORMED)
            bytes.toString(Charsets.ISO_8859_1)
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun checkCurrent(token: Long) {
        if (generation.get() != token) throw CancellationException("Font selection was superseded")
    }
}
