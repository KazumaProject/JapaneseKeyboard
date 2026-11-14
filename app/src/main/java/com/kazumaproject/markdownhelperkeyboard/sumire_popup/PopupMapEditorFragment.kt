package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.doOnLayout
import androidx.preference.PreferenceManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
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
    private lateinit var mapButton: MaterialButton
    private lateinit var keyPicker: FreeFlickKeyPicker
    private lateinit var sourceButton: MaterialButton
    private lateinit var tryButton: MaterialButton
    private lateinit var layoutHeading: TextView
    private var editorReady = false
    private var result = ""
    private val config get() = requireNotNull(model.config)
    private val page get() = requireNotNull(config.map(model.mapId))
    private fun id() = UUID.randomUUID().toString()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun label(id: Int) = getString(id)
    private fun column() = LinearLayout(requireContext()).apply { orientation=LinearLayout.VERTICAL }
    private fun row() = LinearLayout(requireContext()).apply { orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL }
    private fun text(value: String) = TextView(requireContext()).apply { text=value;setPadding(0,dp(6),0,dp(6));textSize=14f }
    private fun button(title: String, action: () -> Unit) = MaterialButton(requireContext(),null,com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        text=title;isAllCaps=false;minWidth=0;minimumWidth=0;maxLines=2
        setPadding(dp(10),dp(6),dp(10),dp(6));setOnClickListener { action() }
    }
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
        content.addView(row().apply {
            item(layoutHeading,1f)
            item(button(label(R.string.free_flick_layout_manage_short)) { guardUnsaved { findNavController().navigate(R.id.freeFlickLayoutsFragment) } })
        })
        if(AppPreference.sumire_keyboard_style!=SumirePopupDefaults.STYLE) content.addView(text(label(R.string.popup_inactive)))
        sourceButton=button(label(R.string.popup_back_editor)) { goSource() }
        content.addView(text(label(R.string.free_flick_choose_key)))
        keyPicker=FreeFlickKeyPicker(requireContext())
        content.addView(keyPicker,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(-dp(16),0,-dp(16),0)
        })
        content.addView(sourceButton)
        mapButton=button("") {showMapChooser()}.apply {contentDescription=label(R.string.popup_map)}
        content.addView(row().apply {
            item(mapButton,1f)
            item(button(label(R.string.free_flick_map_manage)) {}.apply {setOnClickListener {showMapMenu(it)}})
        })
        canvas=PopupMapEditorCanvas(requireContext()).apply { applyFreeFlickTheme();
            inputOptions=FreeFlickPreferences.read(requireContext())
            onSelect={ selected -> model.selectedId=selected;model.assigningMap=false;showProperties() }
            onEdit={properties.doOnLayout {
                properties.requestRectangleOnScreen(android.graphics.Rect(0,0,properties.width,minOf(properties.height,dp(260))),false)
            }}
            onChange={ changed -> model.change(changed);refreshCanvas();showProperties() }
            onBlocked={ conflict -> status.text=conflictMessage(conflict) }
            onTrial={ entered, session ->
                if(entered!=null) result+=entered
                output.text="${label(R.string.popup_result)}：$result"
                status.text=session.mapPath.joinToString(" → ") { this@PopupMapEditorFragment.config.map(it)?.name.orEmpty() } + "　" + session.selectedText.orEmpty()
                mapButton.text=(listOf(this@PopupMapEditorFragment.config.keyId)+session.mapPath.mapNotNull { this@PopupMapEditorFragment.config.map(it)?.name }).joinToString(" › ")+" ▾"
            }
        }
        content.addView(canvas,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(200)))
        // These controls remain directly below the map, independent of the current selection.
        content.addView(row().apply {
            item(button(label(R.string.popup_add)) { addPopup() },1.7f)
            item(button(label(R.string.popup_undo)) { model.undo();refresh() },1f)
        })
        status=text("").apply {
            visibility=View.GONE
            doAfterTextChanged {visibility=if(it.isNullOrEmpty()) View.GONE else View.VISIBLE}
        };content.addView(status)
        output=text("");content.addView(output)
        properties=column();content.addView(properties)
        tryButton=button(label(R.string.popup_try)) {
            model.trying=!model.trying;model.assigningMap=false
            if(model.trying) model.selectedId=null
            refresh()
            canvas.doOnLayout {canvas.requestRectangleOnScreen(android.graphics.Rect(0,0,canvas.width,canvas.height),true)}
        }
        val footer=row().apply {
            setPadding(dp(16),dp(4),dp(16),dp(8))
            item(tryButton,1f)
            item(MaterialButton(requireContext()).apply {
                text=label(R.string.popup_save);isAllCaps=false;minWidth=0;minimumWidth=0
                setOnClickListener {save()}
            },1f)
        }
        content.addView(button(label(R.string.free_flick_template)) { applyTemplate() })
        content.addView(button(label(R.string.free_flick_settings)) {
            findNavController().navigate(R.id.freeFlickSettingsFragment)
        })
        installSelectors()
        refresh()
        editorReady = true
        return column().apply {
            addView(scroll,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
            addView(footer,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))
        }
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
    private fun installSelectors() {
        keyPicker.onKeySelected={ key ->
            if(key!=config.keyId) guardUnsaved {model.load(repository.load(key, model.layoutId));refresh()}
        }
    }
    private fun refresh() {
        layoutHeading.text = getString(R.string.free_flick_layout_editing, FreeFlickLayoutRepository(requireContext()).find(model.layoutId!!).name)
        if(config.map(model.mapId)==null) model.mapId=config.startMapId
        keyPicker.showSelection(FreeFlickLayoutRepository(requireContext()).find(model.layoutId!!).keys.map { if(it.keyId==config.keyId) config else it },config.keyId)
        mapButton.text=(if(model.trying) listOf(config.keyId,config.map(config.startMapId)!!.name)
            else listOf(config.keyId)+model.path.mapNotNull { config.map(it)?.name }+page.name).joinToString(" › ")+" ▾"
        sourceButton.visibility=if(model.path.isEmpty() || model.trying) View.GONE else View.VISIBLE
        sourceButton.text=getString(R.string.free_flick_return_to_map,config.map(model.path.lastOrNull().orEmpty())?.name.orEmpty())
        tryButton.text=label(if(model.trying) R.string.free_flick_return_to_edit else R.string.popup_try)
        status.text=when {
            repository.hasInvalidSavedConfig(config.keyId, model.layoutId) -> label(R.string.popup_invalid_saved)
            model.trying && canvas.inputOptions.presentation==PopupMapPresentation.GUIDE_ABOVE_KEY -> label(R.string.free_flick_try_above)
            model.trying -> label(if(canvas.inputOptions.nearestEnabled) R.string.free_flick_try_nearest else R.string.popup_try_hint)
            config.maps.any { source -> source.regions.any { it.action.type==PopupActionType.SWITCH && it.action.targetMapId==model.mapId } } -> label(R.string.popup_transition_hint)
            else -> ""
        }
        output.text="${label(R.string.popup_result)}：$result";output.visibility=if(model.trying) View.VISIBLE else View.GONE
        refreshCanvas();showProperties()
        keyPicker.keyboard.doOnLayout {
            val key=(0 until keyPicker.keyboard.childCount).map(keyPicker.keyboard::getChildAt)
                .filterIsInstance<TextView>().firstOrNull {it.text.toString()==config.keyId}
            if(key!=null) {
                canvas.setSourceKeySize(key.width.toFloat(),key.height.toFloat())
                fun updateThumbnails(view: View) {
                    if(view is PopupMapThumbnail) {
                        view.tapArea=canvas.tapArea;view.setSourceKeySize(key.width.toFloat(),key.height.toFloat())
                    }
                    if(view is ViewGroup) (0 until view.childCount).forEach {updateThumbnails(view.getChildAt(it))}
                }
                updateThumbnails(properties)
            }
        }
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
    private fun mapCards(maps: List<PopupMap>, selected: String?, onChoose: (PopupMap) -> Unit): LinearLayout = column().apply {
        maps.chunked(2).forEach { pair ->
            addView(row().apply {
                pair.forEach { target ->
                    val card=MaterialCardView(requireContext()).apply {
                        isClickable=true;isFocusable=true;isCheckable=true;isChecked=target.id==selected
                        strokeWidth=dp(if(isChecked) 2 else 1)
                        strokeColor=MaterialColors.getColor(this,if(isChecked) androidx.appcompat.R.attr.colorPrimary else com.google.android.material.R.attr.colorOutline)
                        setCardBackgroundColor(MaterialColors.getColor(this,if(isChecked) com.google.android.material.R.attr.colorPrimaryContainer else com.google.android.material.R.attr.colorSurfaceContainer))
                        contentDescription=getString(R.string.free_flick_map_card,target.name)
                        addView(row().apply {
                            setPadding(dp(8),dp(8),dp(8),dp(8));minimumHeight=dp(72)
                            addView(PopupMapThumbnail(requireContext(),target,canvas.keyWidth.coerceAtLeast(1f),canvas.keyHeight.coerceAtLeast(1f)).apply {tapArea=canvas.tapArea},
                                LinearLayout.LayoutParams(dp(64),dp(56)))
                            item(text(target.name).apply {
                                setPadding(dp(6),0,0,0)
                                setTextColor(MaterialColors.getColor(this,if(target.id==selected)
                                    com.google.android.material.R.attr.colorOnPrimaryContainer else com.google.android.material.R.attr.colorOnSurface))
                            },1f)
                        })
                        setOnClickListener {onChoose(target)}
                    }
                    addView(card,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f).apply {setMargins(dp(3),dp(3),dp(3),dp(3))})
                }
                if(pair.size==1) item(Space(requireContext()),1f)
            })
        }
    }
    private fun showMapChooser() {
        val panel=column().apply {setPadding(dp(16),dp(8),dp(16),dp(8))}
        val dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.free_flick_choose_map)
            .setView(NestedScrollView(requireContext()).apply {addView(panel)})
            .setNegativeButton(R.string.popup_cancel,null).create()
        panel.addView(mapCards(config.maps,model.mapId) { target ->
            model.mapId=target.id;model.path=arrayListOf();model.selectedId=null;model.assigningMap=false;model.trying=false
            dialog.dismiss();refresh()
        })
        panel.addView(button(label(R.string.popup_new_map)) {dialog.dismiss();newMap()})
        dialog.show()
    }
    private fun showProperties() {
        properties.removeAllViews()
        if(model.trying) {
            properties.addView(button(label(R.string.popup_reset_trial)) {result="";output.text="${label(R.string.popup_result)}："})
            return
        }
        val selected=page.regions.firstOrNull { it.id==model.selectedId }
        if(selected==null) {
            properties.addView(text(label(R.string.free_flick_tap_size)))
            properties.addView(text(label(R.string.free_flick_tap_size_scope)))
            val choices=RadioGroup(requireContext())
            val modes=listOf(PopupTapSizeMode.KEY to R.string.free_flick_tap_size_key,
                PopupTapSizeMode.FLICK_SENSITIVITY to R.string.free_flick_tap_size_sensitivity)
            val current=FreeFlickPreferences.read(requireContext()).tapSizeMode
            val ids=modes.associate { (mode,title) ->
                val radio=RadioButton(requireContext()).apply {id=View.generateViewId();text=label(title);minHeight=dp(48)}
                choices.addView(radio);mode to radio.id
            }
            choices.check(ids.getValue(current))
            choices.setOnCheckedChangeListener { _, checked ->
                val mode=ids.entries.first {it.value==checked}.key
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                    .putString(FreeFlickPreferences.TAP_SIZE,mode.preferenceValue).apply()
                canvas.inputOptions=FreeFlickPreferences.read(requireContext());refreshCanvas()
                status.text=""
            }
            properties.addView(choices)
            properties.addView(text(label(R.string.free_flick_tap_size_hint)))
            field(label(R.string.popup_input),page.tapText) { value -> change(config.withMap(page.copy(tapText=value))) }
            return
        }
        val selectedHeading=text("")
        fun updateHeading() {
            val current=page.regions.firstOrNull {it.id==selected.id}
            selectedHeading.text=getString(R.string.free_flick_assign_popup,current?.displayLabel()?.ifEmpty {label(R.string.popup_unset)}.orEmpty())
        }
        properties.addView(selectedHeading);updateHeading()
        val choosing=model.assigningMap || selected.action.type==PopupActionType.SWITCH
        fun setAction(type: PopupActionType) {
            model.assigningMap=false
            updateRegion(selected.id,true) { current ->
                val automaticLabel=current.action.type==PopupActionType.SWITCH && current.label==config.map(current.action.targetMapId.orEmpty())?.name
                current.copy(action=PopupAction(type,current.action.text),label=if(automaticLabel) "" else current.label)
            }
            showProperties()
        }
        properties.addView(row().apply {
            item(button(label(R.string.popup_text)) {setAction(PopupActionType.TEXT)}.apply {isCheckable=true;isChecked=!choosing && selected.action.type==PopupActionType.TEXT},1f)
            item(button(label(R.string.free_flick_open_map)) {model.assigningMap=true;showProperties()}.apply {isCheckable=true;isChecked=choosing},1f)
        })
        if(choosing) {
            val targets=config.maps.filter {it.id!=page.id}
            properties.addView(text(label(if(targets.isEmpty()) R.string.free_flick_no_destination else R.string.free_flick_choose_destination)))
            properties.addView(mapCards(targets,selected.action.targetMapId) { target ->
                if(updateRegion(selected.id,true) { current ->
                    val previousName=config.map(current.action.targetMapId.orEmpty())?.name
                    current.copy(action=PopupAction(PopupActionType.SWITCH,current.action.text,target.id),
                        label=if(current.label.isBlank() || current.label==previousName) target.name else current.label)
                }) model.assigningMap=false
            })
            properties.addView(button(label(R.string.free_flick_create_and_assign)) {newMap(page.id,selected.id)})
            selected.action.targetMapId?.let { targetId ->
                properties.addView(button(getString(R.string.free_flick_edit_destination,config.map(targetId)?.name.orEmpty())) {openMap(targetId)})
            }
        } else when(selected.action.type) {
            PopupActionType.TEXT -> field(label(R.string.popup_input),selected.action.text) { value ->updateRegion(selected.id) {it.copy(action=it.action.copy(text=value))};updateHeading() }
            PopupActionType.BACK -> properties.addView(text(label(R.string.popup_back)))
            else -> Unit
        }
        field(label(R.string.popup_label),selected.label) { value -> updateRegion(selected.id) {it.copy(label=value)};updateHeading() }
        properties.addView(button(label(R.string.popup_back)) {setAction(PopupActionType.BACK)}.apply {
            isCheckable=true;isChecked=!choosing && selected.action.type==PopupActionType.BACK
        })
        properties.addView(row().apply {
            item(button(label(R.string.free_flick_position_size)) {
                canvas.requestRectangleOnScreen(android.graphics.Rect(0,0,canvas.width,canvas.height),true)
                status.text=label(R.string.popup_hint)
            },1f)
            item(button(label(R.string.free_flick_shape_edit)) {
                if(childFragmentManager.findFragmentByTag(PopupShapeEditorDialog.TAG)==null) {
                    val current=page.regions.firstOrNull {it.id==selected.id} ?: return@button
                    PopupShapeEditorDialog.create(page.id,current,current.bounds.width*canvas.keyWidth/(current.bounds.height*canvas.keyHeight))
                        .show(childFragmentManager,PopupShapeEditorDialog.TAG)
                }
            },1f)
            item(button(label(R.string.popup_delete)) {
                if(change(config.withMap(page.copy(regions=page.regions.filterNot {it.id==selected.id})))) {
                    model.selectedId=null;model.assigningMap=false;refresh()
                }
            },.7f)
        })
    }
    private fun updateRegion(regionId: String, redraw: Boolean=false, transform: (PopupRegion)->PopupRegion): Boolean = change(config.withMap(page.copy(regions=page.regions.map {if(it.id==regionId) transform(it) else it})),redraw)
    private fun openMap(mapId: String) {model.path=ArrayList(model.path+model.mapId);model.mapId=mapId;model.selectedId=null;model.assigningMap=false;model.trying=false;refresh()}
    private fun goSource() {
        val source=model.path.lastOrNull() ?: return
        model.path=ArrayList(model.path.dropLast(1));model.mapId=source;model.selectedId=null;model.assigningMap=false;refresh()
    }
    private fun addPopup() {
        if(model.trying) {model.trying=false;refresh()}
        val regionId=id()
        for(y in listOf(-3.6f,-2.6f,-1.6f,-.6f,.6f)) for(x in listOf(-1.5f,-.4f,.7f)) {
            val region=PopupRegion(regionId,canvas.tapArea.bounds.offset(x+.5f,y+.5f))
            val updated=config.withMap(page.copy(regions=page.regions+region))
            if(canvas.canPlace(updated,region.bounds)) {model.change(updated);model.selectedId=regionId;model.assigningMap=false;refresh();return}
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
        val error=text("").apply {visibility=View.GONE};layout.addView(error)
        val dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.popup_new_map).setView(layout).setNegativeButton(R.string.popup_cancel,null)
            .setPositiveButton(if(sourceRegionId!=null) R.string.free_flick_create_and_assign else R.string.popup_create,null).create()
        dialog.setOnShowListener {dialog.getButton(-1).setOnClickListener {
            val title=name.text.toString().trim()
            if(title.isEmpty() || config.maps.any {it.name==title}) {name.error=label(R.string.popup_name_required);return@setOnClickListener}
            val new=if(choices.checkedRadioButtonId==copy.id) page.copy(id=id(),name=title,regions=page.regions.map {it.copy(id=id())}) else PopupMap(id(),title,"")
            var updated=config.copy(maps=config.maps+new)
            if(sourceMapId!=null && sourceRegionId!=null) {
                val source=updated.map(sourceMapId)!!
                updated=updated.withMap(source.copy(regions=source.regions.map { if(it.id==sourceRegionId)
                    it.copy(action=PopupAction(PopupActionType.SWITCH,it.action.text,new.id),label=if(it.label.isBlank()) new.name else it.label) else it }))
                if(PopupMapValidator.conflicts(updated).isNotEmpty()) {
                    error.visibility=View.VISIBLE;error.text=label(R.string.free_flick_assignment_conflict)
                    return@setOnClickListener
                }
            }
            if(change(updated)) {
                dialog.dismiss();openMap(new.id)
            }
        }}
        dialog.show()
    }
    private fun showMapMenu(it: View?) {
        val menu=PopupMenu(requireContext(),it ?: mapButton)
        listOf(R.string.popup_new_map,R.string.popup_rename,R.string.popup_duplicate,R.string.popup_delete).forEach { resource -> menu.menu.add(0,resource,0,resource) }
        menu.setOnMenuItemClickListener { item ->
            when(item.itemId) {
                R.string.popup_new_map -> newMap()
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
    fun leaveEditor() {
        if(model.trying) {model.trying=false;refresh();return}
        if(model.path.isNotEmpty()) {goSource();return}
        guardUnsaved {findNavController().popBackStack()}
    }
    private fun guardUnsaved(next: ()->Unit) {
        if(!model.dirty) {next();return}
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.popup_unsaved)
            .setPositiveButton(R.string.popup_save) {_,_->if(save()) next()}
            .setNegativeButton(R.string.popup_discard) {_,_->next()}
            .setNeutralButton(R.string.popup_cancel,null).show()
    }
}
