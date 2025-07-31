package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_view

interface FloatingDockListener {
    /**
     * View全体がクリックされたときに呼び出されます。
     */
    fun onDockClick()

    /**
     * 右端のアイコンがクリックされたときに呼び出されます。
     */
    fun onIconClick()
}
