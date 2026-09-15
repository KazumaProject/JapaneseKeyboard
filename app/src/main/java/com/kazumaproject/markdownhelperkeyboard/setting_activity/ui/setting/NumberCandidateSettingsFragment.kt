package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.engine.*
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import java.util.UUID

private fun Fragment.column() = LinearLayout(requireContext()).apply {
    orientation = LinearLayout.VERTICAL
    val padding = (16 * resources.displayMetrics.density).toInt()
    setPadding(padding, padding, padding, padding)
}
private fun Fragment.scroll(column: LinearLayout) = ScrollView(requireContext()).apply {
    isFillViewport = true
    addView(column)
}
private fun Fragment.text(parent: LinearLayout, value: CharSequence, heading: Boolean = false): TextView =
    TextView(requireContext()).apply {
        text = value; textSize = if (heading) 20f else 16f
        setPadding(0, 12, 0, 12)
        if (heading) androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
        parent.addView(this)
    }
private fun Fragment.button(parent: LinearLayout, title: String, action: () -> Unit) = Button(requireContext()).apply {
    text = title; isAllCaps = false
    parent.addView(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    setOnClickListener { action() }
}

class NumberCandidateSettingsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View = scroll(column())

    override fun onResume() {
        super.onResume()
        val root = (requireView() as ScrollView).getChildAt(0) as LinearLayout
        root.removeAllViews()
        text(root, getString(R.string.number_types_note))
        if (!AppPreference.japanese_number_candidates_enable_preference) text(root, getString(R.string.number_generation_off))
        val config = AppPreference.number_candidate_config
        val labels = listOf(
            R.string.number_kind_time to R.string.number_example_time,
            R.string.number_kind_people to R.string.number_example_people,
            R.string.number_kind_yen to R.string.number_example_yen,
            R.string.number_kind_date to R.string.number_example_date,
            R.string.number_kind_comma to R.string.number_example_comma,
            R.string.number_kind_large_unit to R.string.number_example_large_unit,
            R.string.number_kind_exponent to R.string.number_example_exponent,
            R.string.number_kind_superscript to R.string.number_example_superscript,
            R.string.number_kind_subscript to R.string.number_example_subscript,
            R.string.number_kind_circled to R.string.number_example_circled,
            R.string.number_kind_black_circled to R.string.number_example_black_circled,
            R.string.number_kind_roman to R.string.number_example_roman,
            R.string.number_kind_parenthesized to R.string.number_example_parenthesized,
            R.string.number_kind_period to R.string.number_example_period,
        )
        NumberCandidateKind.entries.forEachIndexed { index, kind ->
            root.addView(SwitchCompat(requireContext()).apply {
                text = getString(R.string.number_type_row, getString(labels[index].first), getString(labels[index].second))
                setPadding(0, 16, 0, 16); minHeight = (64 * resources.displayMetrics.density).toInt()
                isChecked = kind !in config.disabledKinds
                setOnCheckedChangeListener { _, enabled ->
                    val current = AppPreference.number_candidate_config
                    AppPreference.number_candidate_config = current.copy(disabledKinds =
                        if (enabled) current.disabledKinds - kind else current.disabledKinds + kind)
                }
            })
        }
        text(root, getString(R.string.number_custom_units), true)
        config.units.forEach { unit ->
            root.addView(SwitchCompat(requireContext()).apply {
                text = getString(R.string.number_type_row, unit.output, getString(R.string.number_unit_summary, unit.reading, unit.output, unit.specialReadings.size))
                isChecked = unit.enabled
                setOnCheckedChangeListener { _, enabled ->
                    val current = AppPreference.number_candidate_config
                    AppPreference.number_candidate_config = current.copy(units = current.units.map {
                        if (it.id == unit.id) it.copy(enabled = enabled) else it
                    })
                }
            })
            button(root, getString(R.string.number_edit) + "：" + unit.output) {
                findNavController().navigate(R.id.numberUnitEditorFragment, Bundle().apply { putString("unitId", unit.id) })
            }
        }
        button(root, getString(R.string.number_add_unit)) { findNavController().navigate(R.id.numberUnitEditorFragment) }
    }
}

class NumberUnitEditorFragment : Fragment() {
    private lateinit var editorRoot: LinearLayout
    private lateinit var original: CustomNumberUnit
    private lateinit var output: TextInputEditText
    private lateinit var reading: TextInputEditText
    private lateinit var outputLayout: TextInputLayout
    private lateinit var readingLayout: TextInputLayout
    private lateinit var preview: TextView
    private lateinit var trial: TextInputEditText
    private lateinit var trialResult: TextView
    private lateinit var specialList: LinearLayout
    private lateinit var specialPanel: LinearLayout
    private lateinit var specialValue: TextInputEditText
    private lateinit var specialReading: TextInputEditText
    private lateinit var valueLayout: TextInputLayout
    private lateinit var specialReadingLayout: TextInputLayout
    private lateinit var specialResult: TextView
    private lateinit var specialEffect: TextView
    private lateinit var specialLimit: TextView
    private lateinit var error: TextView
    private val specials = mutableListOf<SpecialNumberReading>()
    private var editing = -1
    private var specialOpen = false
    private var renderedSpecials: List<SpecialNumberReading>? = null
    private var renderedOutput: String? = null
    private var initialized = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val id = state?.getString("id") ?: arguments?.getString("unitId") ?: UUID.randomUUID().toString()
        original = AppPreference.number_candidate_config.units.firstOrNull { it.id == id }
            ?: CustomNumberUnit(id, "", "")
        specials.addAll(if (state == null) original.specialReadings else {
            val values = (state.getLongArray("values") ?: longArrayOf())
            val readings = state.getStringArrayList("readings").orEmpty()
            values.indices.map { SpecialNumberReading(values[it], readings[it]) }
        })
        editing = state?.getInt("editing", -1) ?: -1
        specialOpen = state?.getBoolean("specialOpen") ?: false
    }

    private fun field(parent: LinearLayout, title: String, helper: String? = null, numeric: Boolean = false): Pair<TextInputLayout, TextInputEditText> {
        val layout = TextInputLayout(requireContext()).apply { hint = title; helperText = helper }
        val edit = TextInputEditText(requireContext()).apply {
            id = View.generateViewId()
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        layout.addView(edit)
        parent.addView(layout, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return layout to edit
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        initialized = false
        renderedSpecials = null
        val root = column().also { editorRoot = it }
        text(root, getString(R.string.number_unit_intro))
        field(root, getString(R.string.number_unit_output), getString(R.string.number_unit_output_hint)).also { outputLayout = it.first; output = it.second }
        field(root, getString(R.string.number_unit_reading), getString(R.string.number_unit_reading_hint)).also { readingLayout = it.first; reading = it.second }
        text(root, getString(R.string.number_preview_title), true)
        preview = text(root, "")
        text(root, getString(R.string.number_special_title), true)
        text(root, getString(R.string.number_special_intro))
        specialList = column().also(root::addView)
        button(root, getString(R.string.number_special_add)) {
            if (!validUnitFields()) return@button
            if (specialOpen) { specialValue.requestFocus(); return@button }
            editing = -1; specialOpen = true
            specialValue.setText(""); specialReading.setText("")
            refreshSpecial()
            specialValue.requestFocus()
        }
        specialPanel = column().also(root::addView)
        text(specialPanel, getString(R.string.number_special_add), true)
        field(specialPanel, getString(R.string.number_special_value), numeric = true).also { valueLayout = it.first; specialValue = it.second }
        field(specialPanel, getString(R.string.number_special_reading), getString(R.string.number_special_hint)).also { specialReadingLayout = it.first; specialReading = it.second }
        text(specialPanel, getString(R.string.number_special_result), true)
        specialResult = text(specialPanel, "")
        specialEffect = text(specialPanel, "")
        specialLimit = text(specialPanel, "")
        button(specialPanel, getString(R.string.number_apply_reading)) { saveSpecial() }
        button(specialPanel, getString(R.string.number_cancel)) { specialOpen = false; editing = -1; refreshSpecial() }
        field(root, getString(R.string.number_try_reading), getString(R.string.number_try_hint)).also { trial = it.second }
        trialResult = text(root, "")
        error = text(root, "")
        androidx.core.view.ViewCompat.setAccessibilityLiveRegion(error, androidx.core.view.ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
        button(root, getString(R.string.number_save)) { saveUnit() }
        if (arguments?.getString("unitId") != null) button(root, getString(R.string.number_delete)) {
            AlertDialog.Builder(requireContext()).setMessage(R.string.number_delete_unit)
                .setNegativeButton(R.string.number_cancel, null).setPositiveButton(R.string.number_delete) { _, _ ->
                    val config = AppPreference.number_candidate_config
                    AppPreference.number_candidate_config = config.copy(units = config.units.filterNot { it.id == original.id })
                    findNavController().popBackStack()
                }.show()
        }
        output.setText(state?.getString("output") ?: original.output)
        reading.setText(state?.getString("reading") ?: original.reading)
        trial.setText(state?.getString("trial").orEmpty())
        specialValue.setText(state?.getString("specialValue").orEmpty())
        specialReading.setText(state?.getString("specialReading").orEmpty())
        listOf(output, reading, trial, specialValue, specialReading).forEach { it.doAfterTextChanged { if (initialized) refresh() } }
        initialized = true
        refresh()
        return scroll(root)
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (draft() == original && !specialOpen) { findNavController().popBackStack(); return }
                AlertDialog.Builder(requireContext()).setMessage(R.string.number_discard)
                    .setNegativeButton(R.string.number_cancel, null).setPositiveButton(android.R.string.ok) { _, _ -> findNavController().popBackStack() }.show()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        refreshSpecial()
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putString("id", original.id)
        if (!initialized) return
        out.putString("output", output.text.toString()); out.putString("reading", reading.text.toString())
        out.putString("trial", trial.text.toString()); out.putString("specialValue", specialValue.text.toString())
        out.putString("specialReading", specialReading.text.toString()); out.putBoolean("specialOpen", specialOpen)
        out.putInt("editing", editing)
        out.putLongArray("values", specials.map { it.value }.toLongArray())
        out.putStringArrayList("readings", ArrayList(specials.map { it.reading }))
    }

    private fun draft() = original.copy(output = output.text.toString(), reading = reading.text.toString(), specialReadings = specials.toList())
    private fun candidates(unit: CustomNumberUnit, input: String): String {
        if (!CustomNumberUnit.validReading(input)) return ""
        val config = NumberCandidateConfig(units = listOf(unit.copy(enabled = true)))
        return ValidatedNumber.parseAll(input, config)
            .filter { it.customUnit?.id == unit.id }
            .flatMap { NumberCandidateGenerator.generate(it, PredictionConfig(
                numberCandidateOrder = NumberCandidateOrder.fromPreference(AppPreference.number_candidate_order_preference),
                numberCandidateConfig = config,
            )) }.joinToString("・") { it.string }
    }

    private fun refresh() {
        val unit = draft()
        preview.text = if (unit.isValid()) {
            val example = "に" + unit.reading
            val result = candidates(unit, example)
            if (result.isEmpty()) getString(R.string.number_try_no_match) else "$example → $result"
        } else getString(R.string.number_preview_placeholder)
        trialResult.text = when {
            trial.text.isNullOrEmpty() -> getString(R.string.number_try_empty)
            !unit.isValid() -> getString(R.string.number_unit_reading_hint)
            else -> candidates(unit, trial.text.toString()).let { if (it.isEmpty()) getString(R.string.number_try_no_match) else "${trial.text} → $it" }
        }
        refreshSpecial()
        if (renderedSpecials == specials && renderedOutput == unit.output) return
        renderedSpecials = specials.toList()
        renderedOutput = unit.output
        specialList.removeAllViews()
        specials.forEachIndexed { index, special ->
            text(specialList, "${special.value}${unit.output}　${special.reading} → ${special.value}${unit.output}")
            button(specialList, getString(R.string.number_edit) + "：" + special.reading) {
                if (!validUnitFields()) return@button
                editing = index; specialOpen = true
                specialValue.setText(special.value.toString()); specialReading.setText(special.reading); refreshSpecial()
                specialReading.requestFocus()
            }
            button(specialList, getString(R.string.number_delete) + "：" + special.reading) {
                AlertDialog.Builder(requireContext()).setMessage(R.string.number_delete_special)
                    .setNegativeButton(R.string.number_cancel, null).setPositiveButton(R.string.number_delete) { _, _ ->
                        specials.removeAt(index); specialOpen = false; editing = -1; refresh()
                    }.show()
            }
        }
    }

    private fun refreshSpecial() {
        (requireActivity() as androidx.appcompat.app.AppCompatActivity).supportActionBar?.title = getString(if (specialOpen) R.string.number_special_add else if (arguments?.getString("unitId") == null) R.string.number_add_unit else R.string.number_edit_unit)
        // Focus this step on its two fields, result and scope explanation; the unit draft stays intact.
        for (index in 0 until editorRoot.childCount) {
            val child = editorRoot.getChildAt(index)
            child.visibility = if (child === specialPanel) {
                if (specialOpen) View.VISIBLE else View.GONE
            } else if (specialOpen && child !== error) View.GONE else View.VISIBLE
        }
        if (!specialOpen) return
        val value = specialValue.text.toString().toLongOrNull()?.takeIf { it >= 0 }
        val wholeReading = specialReading.text.toString()
        val unit = draft()
        valueLayout.hint = getString(R.string.number_special_value) + "（${unit.output}）"
        if (value == null || !CustomNumberUnit.validReading(wholeReading) || !CustomNumberUnit.validText(unit.output, 32)) {
            specialResult.text = getString(R.string.number_special_hint); specialEffect.text = ""; specialLimit.text = ""; return
        }
        val temporary = unit.copy(specialReadings = listOf(SpecialNumberReading(value, wholeReading)))
        specialResult.text = getString(R.string.number_conversion_example, wholeReading, candidates(temporary, wholeReading))
        val ordinaryOne = "いち" + unit.reading
        val ordinaryOneRegistered = wholeReading == ordinaryOne || specials.withIndex().any {
            it.index != editing && it.value.value == 1L && it.value.reading == ordinaryOne
        }
        specialEffect.text = if (value == 1L && !ordinaryOneRegistered) getString(R.string.number_special_one_effect, unit.output, wholeReading, unit.reading)
            else getString(R.string.number_special_effect, "$value${unit.output}")
        specialLimit.text = if (value == 1L && unit.reading == "こ" && wholeReading == "いっこ") getString(R.string.number_special_one_limit, unit.output, unit.reading)
            else getString(R.string.number_special_limit, "$value${unit.output}")
    }

    private fun validUnitFields(): Boolean {
        val a = CustomNumberUnit.validText(output.text.toString(), 32)
        val b = CustomNumberUnit.validReading(reading.text.toString())
        outputLayout.error = if (a) null else getString(R.string.number_invalid_output)
        readingLayout.error = if (b) null else getString(R.string.number_invalid_reading)
        return a && b
    }

    private fun saveSpecial() {
        val value = specialValue.text.toString().toLongOrNull()?.takeIf { it >= 0 }
        val whole = specialReading.text.toString()
        valueLayout.error = if (value == null) getString(R.string.number_invalid_value) else null
        val ordinaryValue = if (reading.text.isNullOrEmpty() || !whole.endsWith(reading.text.toString())) null
            else ValidatedNumber.parseReading(whole.dropLast(reading.text!!.length))?.value
        val duplicate = specials.withIndex().any { it.index != editing && it.value.reading == whole } ||
            (ordinaryValue != null && value != null && ordinaryValue != value)
        specialReadingLayout.error = when {
            !CustomNumberUnit.validReading(whole) -> getString(R.string.number_invalid_reading)
            duplicate -> getString(R.string.number_duplicate_reading)
            else -> null
        }
        if (value == null || specialReadingLayout.error != null || !validUnitFields()) return
        if (editing < 0 && specials.size >= 256) { error.text = getString(R.string.number_limit); return }
        val entry = SpecialNumberReading(value, whole)
        if (editing in specials.indices) specials[editing] = entry else specials.add(entry)
        specialOpen = false; editing = -1; error.text = ""; refresh()
    }

    private fun saveUnit() {
        if (!validUnitFields()) return
        if (specialOpen) { error.text = getString(R.string.number_finish_special); return }
        val unit = draft()
        if (!unit.isValid()) { error.text = getString(R.string.number_duplicate_reading); return }
        val config = AppPreference.number_candidate_config
        if (config.units.any { it.id != unit.id && it.output == unit.output && it.reading == unit.reading }) {
            readingLayout.error = getString(R.string.number_duplicate_unit); return
        }
        if (config.units.none { it.id == unit.id } && config.units.size >= 256) { error.text = getString(R.string.number_limit); return }
        val updated = if (config.units.any { it.id == unit.id }) config.units.map { if (it.id == unit.id) unit else it }
            else config.units + unit
        val updatedConfig = config.copy(units = updated)
        // Leave room for kind switches without exceeding the bounded preference decoder.
        if (updatedConfig.encode().length > 900_000) { error.text = getString(R.string.number_limit); return }
        AppPreference.number_candidate_config = updatedConfig
        findNavController().popBackStack()
    }
}
