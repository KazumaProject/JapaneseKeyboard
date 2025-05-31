package com.kazumaproject.core.domain.state

sealed class GestureType {
    data object Null : GestureType()
    data object Down : GestureType()
    data object Tap : GestureType()
    data object FlickLeft : GestureType()
    data object FlickTop : GestureType()
    data object FlickRight : GestureType()
    data object FlickBottom : GestureType()
}