package com.kazumaproject.custom_keyboard.data

import kotlin.math.abs

/** One drag's magnetic guides and continuous, axis-wise collision constraints. */
class PopupBoundsSnapper(
    config: PopupKeyConfig, mapId: String, regionId: String,
    private val viewport: PopupBounds,
    private val keyWidth: Float, private val keyHeight: Float,
    density: Float, private val handle: Int = MOVE
) {
    private data class Guide(val edge: Int, val coordinate: Float)
    private var xGuide: Guide? = null
    private var yGuide: Guide? = null
    private val engage = 8f * density
    private val release = 12f * density
    private val source = requireNotNull(config.map(mapId)).regions.first { it.id == regionId }
    private val obstacles: List<PopupBounds>
    init {
        fun destinations(page: PopupMap, region: PopupRegion): Set<String> = when(region.action.type) {
            PopupActionType.SWITCH -> setOfNotNull(region.action.targetMapId)
            PopupActionType.BACK -> config.maps.filter { parent -> parent.regions.any {
                it.action.type == PopupActionType.SWITCH && it.action.targetMapId == page.id
            } }.map { it.id }.toSet()
            else -> emptySet()
        }
        val page = config.map(mapId)!!
        val targets = destinations(page, source)
        obstacles = listOf(PopupBounds.KEY) + config.maps.flatMap { otherPage -> otherPage.regions.filter { other ->
            !(otherPage.id == mapId && other.id == regionId) &&
                (otherPage.id == mapId || (source.isControl && other.isControl &&
                    (otherPage.id in targets || mapId in destinations(otherPage, other))))
        }.map { it.bounds } }
    }
    private fun edge(b: PopupBounds, index: Int) = when(index) { LEFT -> b.left; RIGHT -> b.right; TOP -> b.top; else -> b.bottom }
    private fun setEdge(b: PopupBounds, index: Int, value: Float) = when(index) {
        LEFT -> if(handle == MOVE) b.copy(left=value,right=value+b.width) else b.copy(left=value)
        RIGHT -> if(handle == MOVE) b.copy(left=value-b.width,right=value) else b.copy(right=value)
        TOP -> if(handle == MOVE) b.copy(top=value,bottom=value+b.height) else b.copy(top=value)
        else -> if(handle == MOVE) b.copy(top=value-b.height,bottom=value) else b.copy(bottom=value)
    }
    private fun magnet(b: PopupBounds, horizontal: Boolean): PopupBounds {
        val edges = if(horizontal) listOf(LEFT,RIGHT) else listOf(TOP,BOTTOM)
        val movable = edges.filter { handle == MOVE || handle and it != 0 }
        val pixels = if(horizontal) keyWidth else keyHeight
        val held = if(horizontal) xGuide else yGuide
        val guide = held?.takeIf { abs(edge(b,it.edge)-it.coordinate)*pixels <= release }
            ?: movable.flatMap { e -> (obstacles + viewport).flatMap { target -> edges.map { Guide(e,edge(target,it)) } } }
                .filter { abs(edge(b,it.edge)-it.coordinate)*pixels <= engage }
                .minByOrNull { abs(edge(b,it.edge)-it.coordinate) }
        if(horizontal) xGuide=guide else yGuide=guide
        return guide?.let { setEdge(b,it.edge,it.coordinate) } ?: b
    }
    fun constrain(current: PopupBounds, requested: PopupBounds): PopupBounds {
        val desired = magnet(magnet(requested,true),false)
        var b = current
        if(handle == MOVE) {
            var dx=(desired.left-b.left).coerceIn(viewport.left-b.left,viewport.right-b.right)
            for(o in obstacles) if(b.top < o.bottom && b.bottom > o.top) {
                if(dx>0 && b.right<=o.left) dx=minOf(dx,o.left-b.right)
                if(dx<0 && b.left>=o.right) dx=maxOf(dx,o.right-b.left)
            }
            b=b.offset(dx,0f)
            var dy=(desired.top-b.top).coerceIn(viewport.top-b.top,viewport.bottom-b.bottom)
            for(o in obstacles) if(b.left < o.right && b.right > o.left) {
                if(dy>0 && b.bottom<=o.top) dy=minOf(dy,o.top-b.bottom)
                if(dy<0 && b.top>=o.bottom) dy=maxOf(dy,o.bottom-b.top)
            }
            b=b.offset(0f,dy)
        } else {
            if(handle and LEFT != 0) {
                var v=desired.left.coerceIn(viewport.left,b.right-MIN_SIZE)
                for(o in obstacles) if(b.top<o.bottom && b.bottom>o.top && b.left>=o.right) v=maxOf(v,o.right)
                b=b.copy(left=v)
            }
            if(handle and RIGHT != 0) {
                var v=desired.right.coerceIn(b.left+MIN_SIZE,viewport.right)
                for(o in obstacles) if(b.top<o.bottom && b.bottom>o.top && b.right<=o.left) v=minOf(v,o.left)
                b=b.copy(right=v)
            }
            if(handle and TOP != 0) {
                var v=desired.top.coerceIn(viewport.top,b.bottom-MIN_SIZE)
                for(o in obstacles) if(b.left<o.right && b.right>o.left && b.top>=o.bottom) v=maxOf(v,o.bottom)
                b=b.copy(top=v)
            }
            if(handle and BOTTOM != 0) {
                var v=desired.bottom.coerceIn(b.top+MIN_SIZE,viewport.bottom)
                for(o in obstacles) if(b.left<o.right && b.right>o.left && b.bottom<=o.top) v=minOf(v,o.top)
                b=b.copy(bottom=v)
            }
        }
        // Floating point addition must not introduce a sliver of overlap at exact contact.
        if(obstacles.any { b.intersects(it) }) return current
        return b
    }
    companion object {
        const val MOVE=0
        const val LEFT=1
        const val RIGHT=2
        const val TOP=4
        const val BOTTOM=8
        const val MIN_SIZE=.35f
    }
}
