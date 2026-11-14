package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import java.util.UUID

class PopupMapEditorFragment : Fragment() {
    private val model: PopupMapEditorViewModel by viewModels()
    private lateinit var repository: SumirePopupRepository
    private lateinit var content: LinearLayout
    private lateinit var properties: LinearLayout
    private lateinit var canvas: PopupMapEditorCanvas
    private lateinit var status: TextView
    private lateinit var output: TextView
    private lateinit var mapSpinner: Spinner
    private lateinit var keyPicker: FreeFlickKeyPicker
    private lateinit var breadcrumb: TextView
    private lateinit var sourceButton: MaterialButton
    private lateinit var editButton: MaterialButton
    private lateinit var tryButton: MaterialButton
    private lateinit var layoutHeading: TextView
    private var editorReady = false
    private var bindingSelectors = false
    private var result = ""
    private val config get() = requireNotNull(model.config)
    private val page get() = requireNotNull(config.map(model.mapId))
    private fun id() = UUID.randomUUID().toString()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun label(id: Int) = getString(id)
    private fun column() = LinearLayout(requireContext()).apply { orientation=LinearLayout.VERTICAL }
    private fun row() = LinearLayout(requireContext()).apply { orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL }
    private fun text(value: String) = TextView(requireContext()).apply { text=value;setPadding(0,dp(6),0,dp(6));textSize=14f }
    private fun button(title: String, action: () -> Unit) = MaterialButton(requireContext(),null,com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text=title;setOnClickListener { action() } }
    private fun LinearLayout.item(view: View, weight: Float=0f) { addView(view,LinearLayout.LayoutParams(if(weight>0) 0 else ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT,weight)) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        editorReady = false
        repository=SumirePopupRepository(requireContext())
        if (model.layoutId == null) model.layoutId = arguments?.getString(FreeFlickLayoutRepository.ARG_LAYOUT_ID)
            ?: runCatching { FreeFlickLayoutRepository(requireContext()).selectedId() }.getOrNull()
        if (model.layoutId == null || runCatching { FreeFlickLayoutRepository(requireContext()).find(model.layoutId!!) }.isFailure) {
            return text(label(R.string.free_flick_layout_operation_error))
        }
        if(model.config==null) model.load(repository.load("は", model.layoutId))
        content=column().apply {setPadding(dp(16),dp(8),dp(16),dp(24));clipToPadding=false}
        val scroll=NestedScrollView(requireContext()).apply {isFillViewport=true;addView(content)}
        layoutHeading = text("")
        content.addView(layoutHeading)
        content.addView(button(label(R.string.free_flick_layouts)) { guardUnsaved { findNavController().navigate(R.id.freeFlickLayoutsFragment) } })
        if(AppPreference.sumire_keyboard_style!=SumirePopupDefaults.STYLE) content.addView(text(label(R.string.popup_inactive)))
        breadcrumb=text("")
        sourceButton=button(label(R.string.popup_back_editor)) { goSource() }
        content.addView(row().apply {
            item(breadcrumb,1f)
            item(sourceButton)
            item(button(label(R.string.popup_save)) { save() })
        })
        content.addView(text(label(R.string.free_flick_choose_key)))
        keyPicker=FreeFlickKeyPicker(requireContext())
        content.addView(keyPicker,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(-dp(16),0,-dp(16),0)
        })
        mapSpinner=Spinner(requireContext()).apply {contentDescription=label(R.string.popup_map)}
        content.addView(row().apply {
            item(text(label(R.string.popup_map)));item(mapSpinner,1f)
        })
        editButton=button(label(R.string.popup_edit)) {model.trying=false;refresh()}.apply {isCheckable=true}
        tryButton=button(label(R.string.popup_try)) {model.trying=true;model.selectedId=null;refresh()}.apply {isCheckable=true}
        content.addView(row().apply {
            item(editButton,1f);item(tryButton,1f)
            item(button(label(R.string.popup_add_map)) {newMap()},1.25f)
            item(button("⋮") {showMapMenu(null)}.apply {minWidth=dp(40);minimumWidth=dp(40);setPadding(dp(8),0,dp(8),0)},.45f)
        })
        canvas=PopupMapEditorCanvas(requireContext()).apply { applyFreeFlickTheme();
            inputOptions=FreeFlickPreferences.read(requireContext())
            onSelect={ selected -> model.selectedId=selected; showProperties() }
            onChange={ changed -> model.change(changed);refreshCanvas();showProperties() }
            onBlocked={ conflict -> status.text=conflictMessage(conflict) }
            onTrial={ entered, session ->
                if(entered!=null) result+=entered
                output.text="${label(R.string.popup_result)}：$result"
                status.text=session.mapPath.joinToString(" → ") { this@PopupMapEditorFragment.config.map(it)?.name.orEmpty() } + "　" + session.selectedText.orEmpty()
            }
        }
        content.addView(canvas,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(240)))
        status=text("");content.addView(status)
        output=text("");content.addView(output)
        properties=column();content.addView(properties)
        content.addView(row().apply {
            item(button(label(R.string.popup_add)) { addPopup() },1f)
            item(button(label(R.string.popup_undo)) { model.undo();refresh() },1f)
        })
        content.addView(button(label(R.string.free_flick_template)) { applyTemplate() })
        content.addView(button(label(R.string.free_flick_settings)) {
            findNavController().navigate(R.id.freeFlickSettingsFragment)
        })
        installSelectors()
        refresh()
        editorReady = true
        return scroll
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (!editorReady) return
        childFragmentManager.setFragmentResultListener(PopupShapeEditorDialog.RESULT, viewLifecycleOwner) { _, result ->
            val target = config.map(result.getString("map").orEmpty()) ?: return@setFragmentResultListener
            val regionId = result.getString("region")
            val outline = com.google.gson.Gson().fromJson(result.getString("outline"), PopupOutline::class.java)
            change(config.withMap(target.copy(regions = target.regions.map { if (it.id == regionId) it.copy(outline = outline) else it })), true)
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,object:OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { leaveEditor() }
        })
    }
    override fun onResume() {
        super.onResume()
        if (editorReady) {
            canvas.applyFreeFlickTheme()
            canvas.inputOptions=FreeFlickPreferences.read(requireContext())
            runCatching {
                val saved = repository.load(config.keyId, model.layoutId)
                if (!model.dirty && saved != config) model.load(saved)
                refresh()
            }.onFailure { status.text=label(R.string.free_flick_layout_operation_error) }
        }
    }
    private fun applyTemplate() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.free_flick_template_12)
            .setMessage(getString(R.string.free_flick_template_confirm,config.keyId))
            .setNegativeButton(R.string.popup_cancel,null)
            .setPositiveButton(R.string.popup_apply) {_,_->
                model.change(SumirePopupDefaults.create(config.keyId))
                model.mapId=config.startMapId;model.path=arrayListOf();model.selectedId=null;model.trying=false
                refresh();status.text=label(R.string.free_flick_template_hint)
            }.show()
    }
    private fun adapter(items: List<String>) = ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,items)
    private fun installSelectors() {
        keyPicker.onKeySelected={ key ->
            if(key!=config.keyId) guardUnsaved {model.load(repository.load(key, model.layoutId));refresh()}
        }
        mapSpinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, rowId: Long) {
                if(bindingSelectors || position !in config.maps.indices || config.maps[position].id==model.mapId) return
                model.mapId=config.maps[position].id;model.path=arrayListOf();model.selectedId=null;refresh()
            }
        }
    }
    private fun refresh() {
        layoutHeading.text = getString(R.string.free_flick_layout_editing, FreeFlickLayoutRepository(requireContext()).find(model.layoutId!!).name)
        bindingSelectors=true
        if(config.map(model.mapId)==null) model.mapId=config.startMapId
        keyPicker.showSelection(FreeFlickLayoutRepository(requireContext()).find(model.layoutId!!).keys.map { if(it.keyId==config.keyId) config else it },config.keyId)
        mapSpinner.adapter=adapter(config.maps.map { it.name });mapSpinner.setSelection(config.maps.indexOfFirst { it.id==model.mapId })
        bindingSelectors=false
        sourceButton.visibility=if(model.path.isEmpty()) View.GONE else View.VISIBLE
        editButton.isChecked=!model.trying
        tryButton.isChecked=model.trying
        breadcrumb.text=(listOf(config.keyId)+model.path.mapNotNull { config.map(it)?.name }+page.name).joinToString(" ／ ")
        status.text=when {
            repository.hasInvalidSavedConfig(config.keyId, model.layoutId) -> label(R.string.popup_invalid_saved)
            model.trying -> label(if(canvas.inputOptions.nearestEnabled) R.string.free_flick_try_nearest else R.string.popup_try_hint)
            config.maps.any { source -> source.regions.any { it.action.type==PopupActionType.SWITCH && it.action.targetMapId==model.mapId } } -> label(R.string.popup_transition_hint)
            else -> label(R.string.popup_hint)
        }
        output.text="${label(R.string.popup_result)}：$result";output.visibility=if(model.trying) View.VISIBLE else View.GONE
        refreshCanvas();showProperties()
    }
    private fun refreshCanvas() { canvas.showConfig(config,if(model.trying) config.startMapId else model.mapId,model.selectedId,model.trying) }
    private fun change(value: PopupKeyConfig, refreshFields: Boolean=false): Boolean {
        val conflicts=PopupMapValidator.conflicts(value)
        if(conflicts.isNotEmpty()) {
            status.text=conflictMessage(conflicts.first())
            Toast.makeText(requireContext(),status.text,Toast.LENGTH_LONG).show()
            return false
        }
        model.change(value);refreshCanvas()
        if(refreshFields) refresh()
        return true
    }
    private fun conflictMessage(conflict: PopupConflict?): String {
        if(conflict==null) return label(R.string.popup_collision)
        if(conflict.type==PopupConflictType.KEY) return getString(R.string.popup_collision_detail,label(R.string.popup_tap),config.keyId)
        val map=config.map(conflict.otherMapId ?: conflict.mapId)
        val region=map?.regions?.firstOrNull {it.id==(conflict.otherRegionId ?: conflict.regionId)}
        return getString(R.string.popup_collision_detail,map?.name.orEmpty(),region?.displayLabel().orEmpty())
    }
    private fun field(title: String, value: String, callback: (String) -> Unit): TextInputEditText {
        val input=TextInputEditText(requireContext()).apply {setText(value);textSize=16f;setSingleLine(false);contentDescription=title}
        properties.addView(TextInputLayout(requireContext()).apply { hint=title;addView(input);setPadding(0,dp(5),0,dp(5)) })
        input.doAfterTextChanged { callback(it?.toString().orEmpty()) }
        return input
    }
    private fun showProperties() {
        properties.removeAllViews()
        if(model.trying) {
            properties.addView(button(label(R.string.popup_reset_trial)) {result="";output.text="${label(R.string.popup_result)}："})
            return
        }
        val selected=page.regions.firstOrNull { it.id==model.selectedId }
        if(selected==null) {
            properties.addView(text("${label(R.string.popup_selection)}：${label(R.string.popup_tap)}"))
            properties.addView(text(label(R.string.popup_fixed)))
            field(label(R.string.popup_input),page.tapText) { value -> change(config.withMap(page.copy(tapText=value))) }
            return
        }
        val selectedHeading=text("")
        fun updateHeading() {
            val current=page.regions.firstOrNull {it.id==selected.id}
            selectedHeading.text="${label(R.string.popup_selection)}：${current?.displayLabel()?.ifEmpty {label(R.string.popup_unset)}}"
        }
        properties.addView(selectedHeading)
        updateHeading()
        properties.addView(button(label(R.string.free_flick_shape_customize)) {
            if (childFragmentManager.findFragmentByTag(PopupShapeEditorDialog.TAG) == null) {
                val current = page.regions.firstOrNull { it.id == selected.id } ?: return@button
                PopupShapeEditorDialog.create(page.id, current, current.bounds.width * canvas.keyWidth / (current.bounds.height * canvas.keyHeight))
                    .show(childFragmentManager, PopupShapeEditorDialog.TAG)
            }
        })
        field(label(R.string.popup_label),selected.label) { value -> updateRegion(selected.id) {it.copy(label=value)};updateHeading() }
        properties.addView(text(label(R.string.popup_action)))
        val actions=listOf(label(R.string.popup_text),label(R.string.popup_switch),label(R.string.popup_back))
        val action=Spinner(requireContext()).apply {adapter=adapter(actions);setSelection(selected.action.type.ordinal)}
        properties.addView(action)
        action.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?,view: View?,position: Int,rowId: Long) {
                val current=page.regions.firstOrNull {it.id==selected.id} ?: return
                val type=PopupActionType.entries[position]
                if(type==current.action.type) return
                val target=if(type==PopupActionType.SWITCH) config.maps.firstOrNull {it.id!=page.id}?.id else null
                if(type==PopupActionType.SWITCH && target==null) { newMap(page.id,current.id);return }
                updateRegion(current.id,true) {it.copy(action=PopupAction(type,current.action.text,target))}
                showProperties()
            }
        }
        when(selected.action.type) {
            PopupActionType.TEXT -> field(label(R.string.popup_input),selected.action.text) { value ->updateRegion(selected.id) {it.copy(action=it.action.copy(text=value))};updateHeading() }
            PopupActionType.SWITCH -> {
                properties.addView(text(label(R.string.popup_target)))
                val maps=config.maps.filter {it.id!=page.id}
                val target=Spinner(requireContext()).apply {adapter=adapter(maps.map {it.name});setSelection(maps.indexOfFirst {it.id==selected.action.targetMapId}.coerceAtLeast(0))}
                properties.addView(target)
                target.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                    override fun onItemSelected(parent: AdapterView<*>?,view: View?,position: Int,rowId: Long) {
                        val current=page.regions.firstOrNull {it.id==selected.id} ?: return
                        val targetId=maps.getOrNull(position)?.id ?: return
                        if(current.action.targetMapId!=targetId && !updateRegion(current.id) {it.copy(action=it.action.copy(targetMapId=targetId))}) showProperties()
                    }
                }
                properties.addView(button(label(R.string.popup_new_map)) {newMap(page.id,selected.id)})
                properties.addView(button(label(R.string.popup_edit_target)) { selected.action.targetMapId?.let { openMap(it) } })
            }
            PopupActionType.BACK -> properties.addView(text(label(R.string.popup_back)))
        }
        properties.addView(button(label(R.string.popup_delete)) {change(config.withMap(page.copy(regions=page.regions.filterNot {it.id==selected.id})),true);model.selectedId=null;showProperties()})
    }
    private fun updateRegion(regionId: String, redraw: Boolean=false, transform: (PopupRegion)->PopupRegion): Boolean = change(config.withMap(page.copy(regions=page.regions.map {if(it.id==regionId) transform(it) else it})),redraw)
    private fun openMap(mapId: String) {model.path=ArrayList(model.path+model.mapId);model.mapId=mapId;model.selectedId=null;refresh()}
    private fun goSource() {
        val source=model.path.lastOrNull() ?: return
        model.path=ArrayList(model.path.dropLast(1));model.mapId=source;model.selectedId=null;refresh()
    }
    private fun addPopup() {
        if(model.trying) {model.trying=false;refresh()}
        val regionId=id()
        for(y in listOf(-3.6f,-2.6f,-1.6f,-.6f,.6f)) for(x in listOf(-1.5f,-.4f,.7f)) {
            val region=PopupRegion(regionId,PopupBounds.KEY.offset(x+.5f,y+.5f))
            val updated=config.withMap(page.copy(regions=page.regions+region))
            if(canvas.canPlace(updated,region.bounds)) {model.change(updated);model.selectedId=regionId;refresh();return}
        }
        status.text=label(R.string.popup_no_space)
    }
    private fun newMap(sourceMapId: String?=null, sourceRegionId: String?=null) {
        val layout=column().apply {setPadding(dp(24),0,dp(24),0)}
        val name=TextInputEditText(requireContext()).apply {hint=label(R.string.popup_name);setText(label(R.string.popup_new_map)+" ${config.maps.size+1}")}
        layout.addView(name)
        val choices=RadioGroup(requireContext())
        val blank=RadioButton(requireContext()).apply {id=View.generateViewId();text=label(R.string.popup_blank)}
        val copy=RadioButton(requireContext()).apply {id=View.generateViewId();text=label(R.string.popup_copy)}
        choices.addView(blank);choices.addView(copy);choices.check(if(sourceMapId==null) copy.id else blank.id);layout.addView(choices)
        val dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.popup_new_map).setView(layout).setNegativeButton(R.string.popup_cancel,null).setPositiveButton(R.string.popup_create,null).create()
        dialog.setOnShowListener {dialog.getButton(-1).setOnClickListener {
            val title=name.text.toString().trim()
            if(title.isEmpty() || config.maps.any {it.name==title}) {name.error=label(R.string.popup_name_required);return@setOnClickListener}
            val new=if(choices.checkedRadioButtonId==copy.id) page.copy(id=id(),name=title,regions=page.regions.map {it.copy(id=id())}) else PopupMap(id(),title,"")
            var updated=config.copy(maps=config.maps+new)
            var linked=true
            if(sourceMapId!=null && sourceRegionId!=null) {
                val source=updated.map(sourceMapId)!!
                val connected=updated.withMap(source.copy(regions=source.regions.map { if(it.id==sourceRegionId) it.copy(action=PopupAction(PopupActionType.SWITCH,targetMapId=new.id)) else it }))
                if(PopupMapValidator.conflicts(connected).isEmpty()) updated=connected else linked=false
            }
            if(change(updated)) {
                dialog.dismiss();openMap(new.id)
                if(!linked) status.text=label(R.string.popup_created_unlinked)
            }
        }}
        dialog.show()
    }
    private fun showMapMenu(it: View?) {
        val menu=PopupMenu(requireContext(),it ?: mapSpinner)
        listOf(R.string.popup_rename,R.string.popup_duplicate,R.string.popup_delete).forEach { resource -> menu.menu.add(0,resource,0,resource) }
        menu.setOnMenuItemClickListener { item ->
            when(item.itemId) {
                R.string.popup_rename -> renameMap()
                R.string.popup_duplicate -> newMap()
                R.string.popup_delete -> deleteMap()
            };true
        };menu.show()
    }
    private fun renameMap() {
        val input=TextInputEditText(requireContext()).apply {setText(page.name)}
        val dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.popup_rename).setView(input).setNegativeButton(R.string.popup_cancel,null).setPositiveButton(R.string.popup_apply,null).create()
        dialog.setOnShowListener {dialog.getButton(-1).setOnClickListener {
            val name=input.text.toString().trim()
            if(name.isEmpty() || config.maps.any {it.id!=page.id && it.name==name}) input.error=label(R.string.popup_name_required)
            else {change(config.withMap(page.copy(name=name)),true);dialog.dismiss()}
        }};dialog.show()
    }
    private fun deleteMap() {
        if(page.id==config.startMapId) {Toast.makeText(requireContext(),R.string.popup_start_delete,Toast.LENGTH_LONG).show();return}
        val references=config.maps.flatMap { source -> source.regions.filter {it.action.type==PopupActionType.SWITCH && it.action.targetMapId==page.id}.map {"${source.name} ／ ${it.displayLabel()}"} }
        if(references.isNotEmpty()) {MaterialAlertDialogBuilder(requireContext()).setMessage(label(R.string.popup_used)+"\n\n"+references.joinToString("\n")).setPositiveButton(android.R.string.ok,null).show();return}
        MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.popup_delete_confirm).setNegativeButton(R.string.popup_cancel,null).setPositiveButton(R.string.popup_delete) {_,_->
            model.change(config.copy(maps=config.maps.filterNot {it.id==page.id}));model.mapId=config.startMapId;model.path=arrayListOf();model.selectedId=null;refresh()
        }.show()
    }
    private fun save(): Boolean {
        return runCatching {model.layoutId=repository.save(config, model.layoutId);model.markSaved();refresh();status.text=label(R.string.popup_saved);true}
            .getOrElse {status.text=label(R.string.free_flick_layout_operation_error);false}
    }
    fun leaveEditor() {guardUnsaved {findNavController().popBackStack()} }
    private fun guardUnsaved(next: ()->Unit) {
        if(!model.dirty) {next();return}
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.popup_unsaved)
            .setPositiveButton(R.string.popup_save) {_,_->if(save()) next()}
            .setNegativeButton(R.string.popup_discard) {_,_->next()}
            .setNeutralButton(R.string.popup_cancel,null).show()
    }
}
