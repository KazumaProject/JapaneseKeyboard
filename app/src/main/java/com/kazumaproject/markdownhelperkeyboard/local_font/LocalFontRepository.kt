package com.kazumaproject.markdownhelperkeyboard.local_font

import android.content.ContentResolver
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
import java.io.Closeable
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.EmptyCoroutineContext
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
    private val stagingFiles = ConcurrentHashMap.newKeySet<String>()
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
                    collectGarbage(activeId = null, previewId = null)
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
                        collectGarbage(activeId = null, previewId = null)
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
                        collectGarbage(activeRecord?.id, preview?.record?.id)
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
        val temp = store.newStagingFile()
        stagingFiles += temp.name
        var publishedFile: File? = null
        var keepPublishedFile = false
        try {
            // Keep the lock around local state and file mutations only. Provider I/O below may
            // block for an arbitrary time and must not prevent restore or another selection.
            operationMutex.withLock {
                currentCoroutineContext().ensureActive()
                checkCurrent(token)
                val oldPreview = preview
                preview = null
                store.delete(oldPreview?.let { store.fileFor(it.record) })
                collectGarbage(activeRecord?.id, null)
            }

            val displayName = queryDisplayName(uri)
            currentCoroutineContext().ensureActive()
            checkCurrent(token)
            val copied = copyUriToFile(uri, temp, token)
            SfntFontValidator.validate(temp)
            val signature = readSignature(temp)
            val extension = if (signature == "OTTO") "otf" else "ttf"
            val record = store.newRecord(displayName, extension, copied.first, copied.second)

            val prepared = operationMutex.withLock {
                currentCoroutineContext().ensureActive()
                checkCurrent(token)
                val file = store.publish(temp, record).also { publishedFile = it }
                val typeface = createTypeface(file)
                currentCoroutineContext().ensureActive()
                checkCurrent(token)
                val prepared = LocalFontPreview(token, record, typeface, displayName)
                preview = prepared
                collectGarbage(activeRecord?.id, record.id)
                prepared
            }
            keepPublishedFile = true
            prepared
        } finally {
            withContext(NonCancellable) {
                operationMutex.withLock {
                    if (!keepPublishedFile && preview?.generation == token) preview = null
                    if (!keepPublishedFile) store.delete(publishedFile)
                }
                store.delete(temp)
                stagingFiles.remove(temp.name)
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
            collectGarbage(activeRecord?.id, null)
        }
    }

    suspend fun cancelPreview(prepared: LocalFontPreview?) = withContext(Dispatchers.IO) {
        if (prepared == null) {
            synchronized(generationGate) { generation.incrementAndGet() }
        }
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
            collectGarbage(activeRecord?.id, preview?.record?.id)
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
            collectGarbage(activeId = null, previewId = null)
        }
    }

    private suspend fun queryDisplayName(uri: Uri): String {
        return try {
            withProviderCancellation { signal, register ->
                val cursor = resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                    signal,
                )
                val cursorResource = cursor?.let(register)
                try {
                    currentCoroutineContext().ensureActive()
                    val name = if (cursor?.moveToFirst() == true) {
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            .takeIf { it >= 0 }
                            ?.let(cursor::getString)
                    } else null
                    LocalFontStore.sanitizeDisplayName(name.orEmpty())
                } finally {
                    cursorResource?.close()
                }
            }
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            "Local font"
        }
    }

    private suspend fun copyUriToFile(uri: Uri, target: File, token: Long): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        return withProviderCancellation { signal, register ->
            val descriptor = resolver.openFileDescriptor(uri, "r", signal)
                ?: throw IOException("Unable to open selected font")
            val descriptorResource = register(descriptor)
            try {
                var total = 0L
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        checkCurrent(token)
                        val count = readProviderChunk(descriptor, buffer, token)
                        if (count == 0) break
                        total += count.toLong()
                        if (total > MAX_LOCAL_FONT_BYTES) throw FontValidationException(FontFormatIssue.TOO_LARGE)
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                    }
                    output.fd.sync()
                }
                if (total == 0L) throw FontValidationException(FontFormatIssue.EMPTY)
                total to digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            } finally {
                descriptorResource.close()
            }
        }
    }

    /**
     * Poll the provider descriptor in short intervals so a provider that leaves a pipe open but
     * stops writing cannot pin this coroutine in FileInputStream.read().
     */
    private suspend fun readProviderChunk(
        descriptor: ParcelFileDescriptor,
        buffer: ByteArray,
        token: Long,
    ): Int {
        val context = currentCoroutineContext()
        val pollFd = StructPollfd().apply {
            fd = descriptor.fileDescriptor
            events = (OsConstants.POLLIN or OsConstants.POLLERR or OsConstants.POLLHUP).toShort()
        }
        while (true) {
            context.ensureActive()
            checkCurrent(token)
            val ready = try {
                Os.poll(arrayOf(pollFd), PROVIDER_READ_POLL_MS)
            } catch (e: ErrnoException) {
                context.ensureActive()
                throw IOException("Unable to read selected font", e)
            }
            context.ensureActive()
            checkCurrent(token)
            if (ready == 0) continue

            val events = pollFd.revents.toInt()
            if (events and OsConstants.POLLNVAL != 0) {
                throw IOException("Selected font stream was closed")
            }
            if (events and (OsConstants.POLLIN or OsConstants.POLLERR or OsConstants.POLLHUP) == 0) {
                continue
            }
            try {
                return Os.read(descriptor.fileDescriptor, buffer, 0, buffer.size)
            } catch (e: ErrnoException) {
                context.ensureActive()
                if (e.errno == OsConstants.EAGAIN) continue
                throw IOException("Unable to read selected font", e)
            }
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private suspend fun <T> withProviderCancellation(
        block: suspend (CancellationSignal, (Closeable) -> Closeable) -> T,
    ): T {
        val context = currentCoroutineContext()
        val job = context[Job] ?: throw IllegalStateException("Provider call has no coroutine job")
        val signal = CancellationSignal()
        val resourceLock = Any()
        var cancelled = false
        var resource: ProviderResourceCloseHandle? = null
        val cancellation = job.invokeOnCompletion(
            onCancelling = true,
            invokeImmediately = true,
        ) { cause ->
            if (cause != null) {
                val toClose = synchronized(resourceLock) {
                    cancelled = true
                    resource.also { resource = null }
                }
                dispatchProviderCleanup(signal, toClose)
            }
        }
        try {
            context.ensureActive()
            return block(signal) { closeable ->
                val handle = ProviderResourceCloseHandle(closeable)
                val closeNow = synchronized(resourceLock) {
                    if (cancelled) true else {
                        resource = handle
                        false
                    }
                }
                if (closeNow) dispatchProviderCleanup(resource = handle)
                handle
            }
        } finally {
            cancellation.dispose()
            val remaining = synchronized(resourceLock) {
                resource.also { resource = null }
            }
            remaining?.close()
        }
    }

    private fun dispatchProviderCleanup(
        signal: CancellationSignal? = null,
        resource: ProviderResourceCloseHandle? = null,
    ) {
        signal?.let { cancellationSignal ->
            Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable {
                runCatching { cancellationSignal.cancel() }
            })
        }
        resource?.closeAsync()
    }

    private fun collectGarbage(activeId: String?, previewId: String?) {
        store.collectGarbage(activeId, previewId, stagingFiles.toSet())
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

    private companion object {
        const val PROVIDER_READ_POLL_MS = 200
    }
}

internal class ProviderResourceCloseHandle(private val resource: Closeable) : Closeable {
    private val claimed = AtomicBoolean(false)

    override fun close() {
        if (claimed.compareAndSet(false, true)) resource.close()
    }

    fun closeAsync() {
        if (claimed.compareAndSet(false, true)) {
            Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable {
                runCatching { resource.close() }
            })
        }
    }
}
