package com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.data

import com.kazumaproject.custom_keyboard.data.FlickDirection

object FlickDirectionMapper {

    // 定義された5方向のみを扱うリスト
    val allowedDirections = listOf(
        FlickDirection.TAP,
        FlickDirection.UP_LEFT_FAR,
        FlickDirection.UP,
        FlickDirection.UP_RIGHT_FAR,
        FlickDirection.DOWN
    )

    // Enumと日本語表示名のペア
    private val directionDisplayMap = mapOf(
        FlickDirection.TAP to "タップ",
        FlickDirection.UP_LEFT_FAR to "左フリック",
        FlickDirection.UP to "上フリック",
        FlickDirection.UP_RIGHT_FAR to "右フリック",
        FlickDirection.DOWN to "下フリック"
    )

    // 許可された方向の日本語表示名リスト（Spinner用）
    val displayNames: List<String> = allowedDirections.mapNotNull { directionDisplayMap[it] }

    // 日本語表示名からFlickDirection Enumを取得
    fun fromDisplayName(displayName: String): FlickDirection? {
        return directionDisplayMap.entries.firstOrNull { it.value == displayName }?.key
    }

    // FlickDirection Enumから日本語表示名を取得
    fun toDisplayName(direction: FlickDirection): String? {
        return directionDisplayMap[direction]
    }
}
