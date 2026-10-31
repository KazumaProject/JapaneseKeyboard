package com.kazumaproject.markdownhelperkeyboard.mushroom

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import com.kazumaproject.markdownhelperkeyboard.R
import java.util.UUID

/** Non-exported bridge: external apps receive only the selected source string. */
class MushroomActivity : ComponentActivity() {
    private var delivered = false
    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val result = it.data?.getStringExtra(REPLACE_KEY)
        complete(if (it.resultCode == RESULT_OK && result != null) RESULT_OK else RESULT_CANCELED, result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (receiver() == null) { finish(); return }
        if (savedInstanceState != null) return
        val request = Intent(ACTION_INTERCEPT).apply {
            addCategory(CATEGORY_REPLACE)
            putExtra(REPLACE_KEY, intent.getStringExtra(REPLACE_KEY).orEmpty())
        }
        if (packageManager.queryIntentActivities(request, 0).isEmpty()) {
            complete(RESULT_UNAVAILABLE)
            return
        }
        try {
            val chosenIntent = Intent(this, ChosenReceiver::class.java).apply {
                data = "mushroom://chosen/${UUID.randomUUID()}".toUri()
                putExtra(EXTRA_RECEIVER, receiver())
            }
            val flags = PendingIntent.FLAG_ONE_SHOT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val sender = PendingIntent.getBroadcast(this, 0, chosenIntent, flags).intentSender
            launcher.launch(Intent.createChooser(request, getString(R.string.mushroom_choose_app), sender))
        } catch (_: android.content.ActivityNotFoundException) {
            complete(RESULT_UNAVAILABLE)
        } catch (_: SecurityException) {
            complete(RESULT_FAILED)
        }
    }

    private fun complete(code: Int, text: String? = null) {
        if (delivered) return
        delivered = true
        receiver()?.send(code, Bundle().apply { if (text != null) putString(REPLACE_KEY, text) })
        finish()
    }

    @Suppress("DEPRECATION")
    private fun receiver(): ResultReceiver? = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(EXTRA_RECEIVER, ResultReceiver::class.java)
    } else intent.getParcelableExtra(EXTRA_RECEIVER)

    /** The chooser fills in the selected component; the external app never receives the receiver. */
    class ChosenReceiver : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(context: Context, intent: Intent) {
            val chosen = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT, ComponentName::class.java)
            } else intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT)
            val receiver = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(EXTRA_RECEIVER, ResultReceiver::class.java)
            } else intent.getParcelableExtra(EXTRA_RECEIVER)
            if (chosen != null) {
                receiver?.send(RESULT_CHOSEN, Bundle().apply { putString(EXTRA_PLUGIN_PACKAGE, chosen.packageName) })
            }
        }
    }

    companion object {
        const val ACTION_INTERCEPT = "com.adamrocker.android.simeji.ACTION_INTERCEPT"
        const val CATEGORY_REPLACE = "com.adamrocker.android.simeji.REPLACE"
        const val REPLACE_KEY = "replace_key"
        const val EXTRA_RECEIVER = "com.kazumaproject.markdownhelperkeyboard.mushroom.RECEIVER"
        const val EXTRA_PLUGIN_PACKAGE = "com.kazumaproject.markdownhelperkeyboard.mushroom.PLUGIN_PACKAGE"
        const val RESULT_UNAVAILABLE = 2
        const val RESULT_FAILED = 3
        const val RESULT_CHOSEN = 4
    }
}
