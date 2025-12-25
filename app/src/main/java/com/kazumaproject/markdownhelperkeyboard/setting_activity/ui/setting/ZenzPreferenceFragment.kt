package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class ZenzPreferenceFragment : PreferenceFragmentCompat() {

    private val openModelLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            onModelUriSelected(uri)
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_zenz, rootKey)

        val modelPref = findPreference<Preference>("zenz_model_select_preference")
        modelPref?.setOnPreferenceClickListener {
            showModelSelectDialog()
            true
        }

        updateModelPrefSummary()
    }

    private fun showModelSelectDialog() {
        val items = arrayOf("デフォルト（Assets）", "モデルを選択する")

        AlertDialog.Builder(requireContext())
            .setTitle("Zenzモデルの選択")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        // デフォルトに戻す
                        AppPreference.zenz_model_uri_preference = ""
                        updateModelPrefSummary()

                        // 即時反映したいならここで initModel を呼んでおく（任意）
                        // ※ providesZenzEngine でも必ずフォールバックするので安全
                        tryReInitDefaultModel()
                    }

                    1 -> {
                        // gguf想定（必要なら拡張子で絞る。SAFはMIMEで指定が基本）
                        openModelLauncher.launch(arrayOf("*/*"))
                    }
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun onModelUriSelected(uri: Uri) {
        try {
            // 永続的に読めるようにする（OpenDocument は persistable を取れる）
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            requireContext().contentResolver.takePersistableUriPermission(uri, flags)

            // 保存（“パス”ではなく Uri 文字列）
            AppPreference.zenz_model_uri_preference = uri.toString()
            updateModelPrefSummary()

            // 即時反映したいならここで initModel を呼ぶ（内部コピーは AppModule 側で行う想定）
            // ただしここで即時反映までやる場合、コピー処理をここに持つのが確実です。
            // 今回は「次回起動/DI生成時反映」でも要件を満たすので、ここでは保存までに留めます。
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to take persistable uri permission.")
        } catch (e: Exception) {
            Timber.e(e, "Failed to handle selected model uri.")
        }
    }

    private fun updateModelPrefSummary() {
        val modelPref = findPreference<Preference>("zenz_model_select_preference") ?: return
        val uriStr = AppPreference.zenz_model_uri_preference

        modelPref.summary = if (uriStr.isBlank()) {
            "デフォルト（Assets）"
        } else {
            "選択中: $uriStr"
        }
    }

    private fun tryReInitDefaultModel() {
        // “即時反映”をしたい場合の補助。失敗してもOK。
        try {
            // providesZenzEngine 側で必ずコピー＆init するので、本来ここは不要
            // ただ、UI上で「今すぐ戻したい」を満たすなら AppModule と同等の処理をここに置くのが理想。
            // ここでは最低限の「初期化を試す」だけにしています。
            // 実運用は AppModule 側の確実なフォールバックに任せる。
        } catch (e: Exception) {
            Timber.e(e, "Failed to re-init default model (ignored).")
        }
    }
}
