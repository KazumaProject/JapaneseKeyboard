package com.kazumaproject.zenz

object ZenzEngine {

    init {
        // CMake の add_library(zenz SHARED ...) と一致させる
        System.loadLibrary("zenz")
    }

    external fun initModel(modelPath: String): Boolean
    external fun cancelCurrent()
    external fun closeModel()

    external fun setRuntimeConfig(
        nCtx: Int,
        nThreads: Int
    )

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

    external fun generateWithContextAndConditionsV32(
        profile: String,
        topic: String,
        style: String,
        preference: String,
        leftContext: String,
        rightContext: String,
        input: String,
        maxTokens: Int
    ): String

    external fun candidateEvaluate(
        profile: String?,
        topic: String?,
        style: String?,
        preference: String?,
        leftContext: String?,
        input: String,
        candidate: String
    ): String

    external fun candidateEvaluateV32(
        profile: String?,
        topic: String?,
        style: String?,
        preference: String?,
        leftContext: String?,
        rightContext: String?,
        input: String,
        candidate: String
    ): String

    external fun scoreCandidates(
        profile: String?,
        topic: String?,
        style: String?,
        preference: String?,
        leftContext: String?,
        input: String,
        candidates: Array<String>
    ): FloatArray

    external fun scoreCandidatesV32(
        profile: String?,
        topic: String?,
        style: String?,
        preference: String?,
        leftContext: String?,
        rightContext: String?,
        input: String?,
        candidates: Array<String>
    ): FloatArray
}
