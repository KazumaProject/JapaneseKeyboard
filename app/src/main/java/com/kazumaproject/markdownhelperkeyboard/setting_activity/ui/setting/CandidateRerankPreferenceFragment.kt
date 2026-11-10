package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ai.rerank.CandidateScoringModels
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CandidateRerankPreferenceFragment : AsyncPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_candidate_rerank

    @Inject lateinit var appPreference: AppPreference

    private val scorerModels by lazy { CandidateScoringModels(requireContext().applicationContext) }
    private var importing = false
    private val openModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importScorerFile(it, tokenizer = false) }
    }
    private val openTokenizer = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importScorerFile(it, tokenizer = true) }
    }

    override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {
        findPreference<Preference>("candidate_rerank_zenz_setup")?.setOnPreferenceClickListener {
            navigateSafely(
                R.id.zenzPreferenceFragment,
                bundleOf(
                    CommonPreferenceFragment.ARG_HIGHLIGHT_PREFERENCE_KEY to "enable_ai_conversion_zenz_preference",
                ),
            )
        }
        findPreference<Preference>("candidate_scoring_model")?.setOnPreferenceClickListener {
            openModel.launch(arrayOf("*/*"))
            true
        }
        findPreference<Preference>("candidate_scoring_tokenizer")?.setOnPreferenceClickListener {
            openTokenizer.launch(arrayOf("*/*"))
            true
        }
        findPreference<Preference>("candidate_scoring_clear")?.setOnPreferenceClickListener {
            scorerModels.clear()
            updateScorerPreferences()
            true
        }
        updateScorerPreferences()
    }

    override fun onPreferencesResumed() {
        updateScorerPreferences()
    }

    private fun importScorerFile(uri: Uri, tokenizer: Boolean) {
        lifecycleScope.launch {
            importing = true
            updateScorerPreferences()
            try {
                scorerModels.import(uri, tokenizer)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                context?.let {
                    Toast.makeText(it, R.string.candidate_scoring_import_failed, Toast.LENGTH_LONG).show()
                }
            } finally {
                importing = false
                if (view != null) updateScorerPreferences()
            }
        }
    }

    private fun updateScorerPreferences() {
        val modelName = scorerModels.label(tokenizer = false)
        val tokenizerName = scorerModels.label(tokenizer = true)
        val requiresTokenizer = scorerModels.requiresTokenizer()
        val selection = scorerModels.selection()
        val awaitingTokenizer = requiresTokenizer && selection == null
        val selectedMethod = when {
            selection != null -> getString(R.string.candidate_rerank_current_custom, modelName.orEmpty())
            appPreference.enable_zenz_rerank_preference -> getString(R.string.candidate_rerank_current_zenz)
            else -> getString(R.string.candidate_rerank_current_dictionary)
        }
        findPreference<Preference>("candidate_rerank_current")?.summary = if (awaitingTokenizer) {
            getString(R.string.candidate_rerank_pending_tokenizer, selectedMethod)
        } else selectedMethod

        findPreference<Preference>("candidate_scoring_model")?.apply {
            summary = modelName ?: getString(R.string.candidate_scoring_model_summary)
            isEnabled = !importing
        }
        findPreference<Preference>("candidate_scoring_tokenizer")?.apply {
            summary = when {
                !requiresTokenizer -> getString(R.string.candidate_scoring_tokenizer_optional_summary)
                tokenizerName != null -> tokenizerName
                else -> getString(R.string.candidate_scoring_tokenizer_summary)
            }
            isEnabled = !importing && requiresTokenizer
        }
        findPreference<Preference>("candidate_scoring_clear")?.isEnabled =
            !importing && (modelName != null || tokenizerName != null)
    }
}
