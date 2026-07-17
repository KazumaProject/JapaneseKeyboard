package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter

import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import java.util.UUID

data class FlickLongPressMappingItem(
    val id: String = UUID.randomUUID().toString(),
    val direction: TfbiFlickDirection,
    var output: String
)
