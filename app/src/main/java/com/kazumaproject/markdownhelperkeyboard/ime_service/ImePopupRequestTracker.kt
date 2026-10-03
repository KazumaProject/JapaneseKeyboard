package com.kazumaproject.markdownhelperkeyboard.ime_service

/** Only the latest request in the current input/view session may publish a popup. */
internal class ImePopupRequestTracker {
    private var generation = 0L

    fun begin(): Long = ++generation
    fun invalidate() { generation++ }
    fun isCurrent(request: Long): Boolean = request == generation
}
