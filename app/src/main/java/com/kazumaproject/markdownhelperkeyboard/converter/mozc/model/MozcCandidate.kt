package com.kazumaproject.markdownhelperkeyboard.converter.mozc.model

data class MozcCandidate(
    var key: String = "",
    var value: String = "",
    var contentKey: String = "",
    var contentValue: String = "",
    var consumedKeySize: Int = 0,
    var prefix: String = "",
    var suffix: String = "",
    var description: String = "",
    var a11yDescription: String = "",
    var displayValue: String = "",
    var usageId: Int = 0,
    var usageTitle: String = "",
    var usageDescription: String = "",
    var cost: Int = 0,
    var wcost: Int = 0,
    var structureCost: Int = 0,
    var lid: Short = 0,
    var rid: Short = 0,
    var attributes: Int = 0,
    var category: MozcCandidateCategory = MozcCandidateCategory.DEFAULT,
)

enum class MozcCandidateCategory {
    DEFAULT,
    SYMBOL,
    OTHER,
}
