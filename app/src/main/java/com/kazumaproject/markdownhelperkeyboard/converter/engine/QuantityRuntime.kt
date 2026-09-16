package com.kazumaproject.markdownhelperkeyboard.converter.engine

import android.content.Context
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.SystemNgramRuntime
import com.kazumaproject.quantity.QuantityDictionary
import com.kazumaproject.quantity.QuantityScoringModel
import timber.log.Timber

object QuantityRuntime {
    @Volatile var dictionary: QuantityDictionary = QuantityDictionary.EMPTY
        private set
    @Volatile var scoringModel: QuantityScoringModel? = null
        private set

    fun installScoringModel(value: QuantityScoringModel?, posFingerprint: String, connectionFingerprint: String): Boolean {
        val compatible = value == null || value.posFingerprint == posFingerprint && value.connectionFingerprint == connectionFingerprint
        scoringModel = value.takeIf { compatible }
        return compatible
    }

    @Volatile private var loadAttempted = false

    val rules: List<List<QuantityDictionary.Feature>>
        get() = if (SystemNgramRuntime.isEnabled()) dictionary.rules else emptyList()

    fun install(value: QuantityDictionary) { dictionary = value }

    fun initialize(context: Context) {
        if (dictionary !== QuantityDictionary.EMPTY || loadAttempted) return
        synchronized(this) {
            if (dictionary !== QuantityDictionary.EMPTY || loadAttempted) return
            loadAttempted = true
            dictionary = runCatching {
                context.assets.open("quantity/quantity.dat").use { QuantityDictionary.read(it.readBytes()) }
            }.onFailure {
                Timber.w(it, "Quantity dictionary is unavailable; retaining ordinary dictionary conversion.")
            }.getOrDefault(QuantityDictionary.EMPTY)
            runCatching {
                val bytes = context.assets.open("quantity/scoring-v1.dat").use { it.readBytes() }
                val model = QuantityScoringModel.read(bytes)
                fun fingerprint(input: java.io.InputStream): String = input.use { stream ->
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                    digest.digest().joinToString("") { "%02x".format(it) }
                }
                val pos = fingerprint(context.assets.open("id.def"))
                val connection = java.util.zip.ZipInputStream(context.assets.open("connectionId.dat.zip")).use { zip ->
                    require(zip.nextEntry != null)
                    fingerprint(zip)
                }
                require(installScoringModel(model, pos, connection)) { "Quantity scoring model dictionary generation mismatch" }
            }.onFailure {
                scoringModel = null // Optional asset: retain the complete v5 compatibility path.
                if (it !is java.io.FileNotFoundException) Timber.w(it, "Quantity scoring model is unavailable.")
            }
        }
    }
}
