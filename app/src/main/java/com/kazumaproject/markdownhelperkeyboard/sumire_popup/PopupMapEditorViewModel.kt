package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.kazumaproject.custom_keyboard.data.PopupKeyConfig

class PopupMapEditorViewModel(private val state: SavedStateHandle) : ViewModel() {
    var layoutId: String?
        get() = state["layoutId"]
        set(value) { state["layoutId"] = value }
    private val gson = Gson()
    var config: PopupKeyConfig? = state.get<String>("draft")?.let { gson.fromJson(it, PopupKeyConfig::class.java) }
        private set
    var mapId: String
        get() = state["map"] ?: config?.startMapId.orEmpty()
        set(value) { state["map"] = value }
    var selectedId: String?
        get() = state["selected"]
        set(value) { state["selected"] = value }
    var assigningMap: Boolean
        get() = state["assigningMap"] ?: false
        set(value) { state["assigningMap"] = value }
    var trying: Boolean
        get() = state["trying"] ?: false
        set(value) { state["trying"] = value }
    var path: ArrayList<String>
        get() = state["path"] ?: arrayListOf()
        set(value) { state["path"] = value }
    private var undo: ArrayList<String>
        get() = state["undo"] ?: arrayListOf()
        set(value) { state["undo"] = value }
    val dirty get() = config != null && gson.toJson(config) != state.get<String>("baseline")
    fun load(value: PopupKeyConfig) {
        config = value; state["draft"] = gson.toJson(value); state["baseline"] = gson.toJson(value)
        mapId = value.startMapId; selectedId = null; path = arrayListOf(); undo = arrayListOf(); trying = false; assigningMap = false
    }
    fun change(value: PopupKeyConfig) {
        if (value == config) return
        undo = ArrayList((undo + gson.toJson(config)).takeLast(50))
        config = value; state["draft"] = gson.toJson(value)
    }
    fun markSaved() { state["baseline"] = gson.toJson(config) }
    fun undo(): Boolean {
        val previous = undo.lastOrNull() ?: return false
        undo = ArrayList(undo.dropLast(1))
        config = gson.fromJson(previous, PopupKeyConfig::class.java); state["draft"] = previous
        if (config?.map(mapId) == null) mapId = config!!.startMapId
        selectedId = selectedId?.takeIf { id -> config!!.map(mapId)!!.regions.any { it.id == id } }
        assigningMap = false
        return true
    }
}
