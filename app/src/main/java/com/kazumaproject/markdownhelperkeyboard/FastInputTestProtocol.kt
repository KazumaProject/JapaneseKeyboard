package com.kazumaproject.markdownhelperkeyboard

import android.content.Intent

/** Debug-only synchronization contract between the fast-input host and the IME. */
internal object FastInputTestProtocol {
    const val PRIVATE_IME_OPTION_PREFIX = "fast-input-ready:"
    const val ACTION_IME_READY =
        "com.kazumaproject.markdownhelperkeyboard.action.FAST_INPUT_IME_READY"
    const val EXTRA_TOKEN = "fast_input_ready_token"

    fun privateImeOptions(token: String): String = PRIVATE_IME_OPTION_PREFIX + token

    fun tokenFrom(privateImeOptions: String?): String? = privateImeOptions
        ?.takeIf { it.startsWith(PRIVATE_IME_OPTION_PREFIX) }
        ?.removePrefix(PRIVATE_IME_OPTION_PREFIX)
        ?.takeIf { it.isNotBlank() }

    fun readyIntent(packageName: String, token: String): Intent = Intent(ACTION_IME_READY)
        .setPackage(packageName)
        .putExtra(EXTRA_TOKEN, token)
}
