package com.kazumaproject.markdownhelperkeyboard.ime_service.models

sealed class CandidateEvaluationResult {
    /** エラーが発生した場合 */
    data object Error : CandidateEvaluationResult()

    /** 候補がモデルの予測と一致した場合 */
    data class Pass(val score: Float) : CandidateEvaluationResult()

    /** 候補がモデルの予測と一致しなかった場合。prefixは一致した接頭辞 + モデルが予測したトークン */
    data class FixRequired(val prefix: String) : CandidateEvaluationResult()

    /** EOSが先に出現した場合。resultはその位置までの結果 */
    data class WholeResult(val result: String) : CandidateEvaluationResult()

    companion object {
        /**
         * JNI から返された文字列を解析して CandidateEvaluationResult に変換する
         * フォーマット:
         * - "PASS:<score>"
         * - "FIX:<prefix>"
         * - "WHOLE:<result>"
         * - "ERROR"
         */
        fun parse(raw: String?): CandidateEvaluationResult {
            return when {
                null == raw -> Error
                raw.startsWith("PASS:") -> {
                    val score = raw.removePrefix("PASS:").toFloatOrNull() ?: 0f
                    Pass(score)
                }

                raw.startsWith("FIX:") -> {
                    val prefix = raw.removePrefix("FIX:")
                    FixRequired(prefix)
                }

                raw.startsWith("WHOLE:") -> {
                    val result = raw.removePrefix("WHOLE:")
                    WholeResult(result)
                }

                else -> Error
            }
        }
    }
}
