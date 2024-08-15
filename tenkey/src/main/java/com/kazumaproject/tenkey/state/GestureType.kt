package com.kazumaproject.tenkey.state

sealed class GestureType {

    object Null: GestureType()
    object Tap: GestureType()
    object FlickLeft: GestureType()
    object FlickTop: GestureType()
    object FlickRight: GestureType()
    object FlickBottom: GestureType()
}