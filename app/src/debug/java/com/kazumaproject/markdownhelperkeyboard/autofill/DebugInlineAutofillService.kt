package com.kazumaproject.markdownhelperkeyboard.autofill

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.Field
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.Presentations
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.util.Log
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.kazumaproject.markdownhelperkeyboard.R

/**
 * Debug-only deterministic AutofillService used to exercise the real framework/IME pipeline.
 */
@RequiresApi(Build.VERSION_CODES.R)
class DebugInlineAutofillService : AutofillService() {

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        val fields = structure?.let(::findAutofillFields).orEmpty()
        if (fields.isEmpty() || cancellationSignal.isCanceled) {
            Log.d(TAG, "No supported fields in debug Autofill request")
            callback.onSuccess(null)
            return
        }

        val inlineRequest = request.inlineSuggestionsRequest
        val response = FillResponse.Builder().apply {
            QA_DATASETS.forEachIndexed { index, qaDataset ->
                addDataset(buildDataset(fields, qaDataset, inlineRequest, index))
            }
        }.build()
        Log.d(
            TAG,
            "Returning ${QA_DATASETS.size} datasets for ${fields.size} fields; " +
                "inline=${inlineRequest != null}",
        )
        callback.onSuccess(response)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    private fun findAutofillFields(structure: AssistStructure): List<AutofillField> = buildList {
        for (windowIndex in 0 until structure.windowNodeCount) {
            collectFields(structure.getWindowNodeAt(windowIndex).rootViewNode, this)
        }
    }

    private fun collectFields(
        node: AssistStructure.ViewNode,
        destination: MutableList<AutofillField>,
    ) {
        val id = node.autofillId
        val kind = resolveFieldKind(node)
        if (id != null && kind != null) {
            destination += AutofillField(id, kind)
        }
        for (childIndex in 0 until node.childCount) {
            collectFields(node.getChildAt(childIndex), destination)
        }
    }

    private fun resolveFieldKind(node: AssistStructure.ViewNode): FieldKind? {
        val hints = node.autofillHints.orEmpty().map(String::lowercase)
        return when {
            hints.any { it.contains("password") } -> FieldKind.Password
            hints.any { it.contains("username") || it.contains("email") } -> FieldKind.Username
            node.inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD != 0 ->
                FieldKind.Password
            else -> null
        }
    }

    // SlicedContent.slice is the bridge required by InlinePresentation. AndroidX
    // exposes it publicly but currently annotates the accessor as RestrictedApi.
    @SuppressLint("RestrictedApi")
    private fun buildDataset(
        fields: List<AutofillField>,
        qaDataset: QaDataset,
        inlineRequest: InlineSuggestionsRequest?,
        datasetIndex: Int,
    ): Dataset {
        val menuPresentation = menuPresentation(qaDataset.label)
        val inlinePresentation = inlineRequest?.let { request ->
            val specs = request.inlinePresentationSpecs
            if (specs.isEmpty()) null else {
                val spec = specs[datasetIndex.coerceAtMost(specs.lastIndex)]
                InlinePresentation(
                    InlineSuggestionUi.newContentBuilder(attributionPendingIntent(datasetIndex))
                        .setTitle(qaDataset.label)
                        .build()
                        .slice,
                    spec,
                    false,
                )
            }
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            buildModernDataset(fields, qaDataset, menuPresentation, inlinePresentation)
        } else {
            buildLegacyDataset(fields, qaDataset, menuPresentation, inlinePresentation)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun buildModernDataset(
        fields: List<AutofillField>,
        qaDataset: QaDataset,
        menuPresentation: RemoteViews,
        inlinePresentation: InlinePresentation?,
    ): Dataset {
        val builder = Dataset.Builder()
        fields.forEach { field ->
            val presentations = Presentations.Builder()
                .setMenuPresentation(menuPresentation)
                .apply {
                    inlinePresentation?.let(::setInlinePresentation)
                }
                .build()
            val value = AutofillValue.forText(qaDataset.valueFor(field.kind))
            builder.setField(
                field.id,
                Field.Builder()
                    .setValue(value)
                    .setPresentations(presentations)
                    .build(),
            )
        }
        return builder.build()
    }

    @Suppress("DEPRECATION")
    private fun buildLegacyDataset(
        fields: List<AutofillField>,
        qaDataset: QaDataset,
        menuPresentation: RemoteViews,
        inlinePresentation: InlinePresentation?,
    ): Dataset {
        val builder = Dataset.Builder(menuPresentation)
        fields.forEach { field ->
            val value = AutofillValue.forText(qaDataset.valueFor(field.kind))
            if (inlinePresentation == null) {
                builder.setValue(field.id, value, menuPresentation)
            } else {
                builder.setValue(field.id, value, menuPresentation, inlinePresentation)
            }
        }
        return builder.build()
    }

    private fun menuPresentation(label: String): RemoteViews {
        return RemoteViews(packageName, R.layout.debug_inline_autofill_menu_item).apply {
            setTextViewText(R.id.debug_inline_autofill_label, label)
        }
    }

    private fun attributionPendingIntent(datasetIndex: Int): PendingIntent {
        val intent = Intent(this, DebugInlineAutofillAttributionActivity::class.java)
        return PendingIntent.getActivity(
            this,
            datasetIndex,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    private data class AutofillField(
        val id: AutofillId,
        val kind: FieldKind,
    )

    private enum class FieldKind { Username, Password }

    private data class QaDataset(
        val label: String,
        val username: String,
        val password: String,
    ) {
        fun valueFor(kind: FieldKind): String = when (kind) {
            FieldKind.Username -> username
            FieldKind.Password -> password
        }
    }

    private companion object {
        const val TAG = "SumireInlineAutofillQA"

        val QA_DATASETS = listOf(
            QaDataset(
                label = "🔐 個人アカウント",
                username = "personal@example.test",
                password = "sumire-personal-password",
            ),
            QaDataset(
                label = "🔐 仕事用",
                username = "work@example.test",
                password = "sumire-work-password",
            ),
        )
    }
}
