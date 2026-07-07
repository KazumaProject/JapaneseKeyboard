package com.kazumaproject.zenz

object ZenzEngine {
    fun initModel(modelPath: String) = Unit

    fun setRuntimeConfig(
        nCtx: Int,
        nThreads: Int
    ) = Unit

    fun generate(
        prompt: String,
        maxTokens: Int
    ): String = ""

    fun generateWithContext(
        leftContext: String,
        input: String,
        maxTokens: Int
    ): String = ""

    fun generateWithContextAndConditions(
        profile: String,
        topic: String,
        style: String,
        preference: String,
        leftContext: String,
        input: String,
        maxTokens: Int
    ): String = ""

    fun generateWithContextAndConditionsV32(
        profile: String,
        topic: String,
        style: String,
        preference: String,
        leftContext: String,
        rightContext: String,
        input: String,
        maxTokens: Int
    ): String = ""

    fun candidateEvaluate(
        profile: String?,
        topic: String?,
        style: String?,
        preference: String?,
        leftContext: String?,
        input: String,
        candidate: String
    ): String = ""

    fun candidateEvaluateV32(
        profile: String?,
        topic: String?,
        style: String?,
        preference: String?,
        leftContext: String?,
        rightContext: String?,
        input: String,
        candidate: String
    ): String = ""

    fun scoreCandidates(
        profile: String?,
        topic: String?,
        style: String?,
        preference: String?,
        leftContext: String?,
        input: String,
        candidates: Array<String>
    ): FloatArray = FloatArray(candidates.size)

    fun scoreCandidatesV32(
        profile: String?,
        topic: String?,
        style: String?,
        preference: String?,
        leftContext: String?,
        rightContext: String?,
        input: String?,
        candidates: Array<String>
    ): FloatArray = FloatArray(candidates.size)
}
