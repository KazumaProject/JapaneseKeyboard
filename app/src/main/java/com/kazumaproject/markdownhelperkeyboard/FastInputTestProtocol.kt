package com.kazumaproject.markdownhelperkeyboard

import android.content.Intent
import android.os.Bundle
import android.os.ResultReceiver

/** Debug-only synchronization contract between the fast-input host and the IME. */
internal object FastInputTestProtocol {
    const val PRIVATE_IME_OPTION_PREFIX = "fast-input-ready:"
    const val ACTION_IME_READY =
        "com.kazumaproject.markdownhelperkeyboard.action.FAST_INPUT_IME_READY"
    const val EXTRA_TOKEN = "fast_input_ready_token"
    const val ACTION_RESET_FOR_TEST =
        "com.kazumaproject.markdownhelperkeyboard.action.FAST_INPUT_RESET"
    const val EXTRA_RESET_TOKEN = "fast_input_reset_token"
    const val EXTRA_TRACE_ID = "fast_input_trace_id"
    const val EXTRA_RESET_RESULT_RECEIVER = "fast_input_reset_result_receiver"
    const val EXTRA_RESET_ERROR = "fast_input_reset_error"
    const val RESET_ACK = 1
    const val RESET_ERROR = -1

    fun privateImeOptions(token: String): String = PRIVATE_IME_OPTION_PREFIX + token

    fun tokenFrom(privateImeOptions: String?): String? = privateImeOptions
        ?.takeIf { it.startsWith(PRIVATE_IME_OPTION_PREFIX) }
        ?.removePrefix(PRIVATE_IME_OPTION_PREFIX)
        ?.takeIf { it.isNotBlank() }

    fun readyIntent(packageName: String, token: String): Intent = Intent(ACTION_IME_READY)
        .setPackage(packageName)
        .putExtra(EXTRA_TOKEN, token)

    fun resetCommand(
        token: String,
        receiver: ResultReceiver,
        traceId: String? = null,
    ): Bundle = Bundle().apply {
        putString(EXTRA_RESET_TOKEN, token)
        traceId?.takeIf(String::isNotBlank)?.let {
            putString(EXTRA_TRACE_ID, it)
        }
        putParcelable(EXTRA_RESET_RESULT_RECEIVER, receiver)
    }
}
