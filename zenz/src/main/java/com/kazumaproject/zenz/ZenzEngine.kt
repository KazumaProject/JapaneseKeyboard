package com.kazumaproject.zenz

object ZenzEngine {

    init {
        // CMake の add_library(zenz SHARED ...) と一致させる
        System.loadLibrary("zenz")
    }

    external fun initModel(modelPath: String)
    external fun generate(
        prompt: String,
        maxTokens: Int
    ): String

    external fun generateWithContext(
        leftContext: String,
        input: String,
        maxTokens: Int
    ): String

    external fun generateWithContextAndConditions(
        profile: String,
        topic: String,
        style: String,
        preference: String,
        leftContext: String,
        input: String,
        maxTokens: Int
    ): String
}
