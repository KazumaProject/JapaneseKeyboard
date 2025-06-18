package com.kazumaproject.core.domain.cryptoManager

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystoreを使用したAES暗号化・復号を管理するクラス
 */
object CryptoManager {

    private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
    private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
    private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
    private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"

    private const val PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "user_dictionary_key"

    private val keyStore = KeyStore.getInstance(PROVIDER).apply {
        load(null)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        // 既存のキーを取得するか、なければ新しいキーを生成する
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: generateSecretKey()
    }

    private fun generateSecretKey(): SecretKey {
        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(BLOCK_MODE)
            .setEncryptionPaddings(PADDING)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // 必要に応じてtrueに変更
            .build()

        val keyGenerator = KeyGenerator.getInstance(ALGORITHM, PROVIDER)
        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    /**
     * データを暗号化する
     * @param data 暗号化する平文のByteArray
     * @return 暗号化されたデータ（IV + 暗号文）
     */
    fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data)
        return iv + encryptedData // IVを先頭に付けて返す
    }

    /**
     * データを復号する
     * @param encryptedData IV付きの暗号化されたByteArray
     * @return 復号された平文のByteArray
     */
    fun decrypt(encryptedData: ByteArray): ByteArray {
        // 最初の12バイトはIV
        val iv = encryptedData.copyOfRange(0, 12)
        val data = encryptedData.copyOfRange(12, encryptedData.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
        return cipher.doFinal(data)
    }
}
