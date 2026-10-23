package com.kazumaproject.markdownhelperkeyboard.gemma.runtime;

import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.IGemmaRuntimeCallback;
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.IGemmaScoringCallback;

oneway interface IGemmaRuntime {
    void initialize(String modelPath, String backend, int modalityFlags,
        IGemmaRuntimeCallback callback);
    void generate(long requestId, String prompt, String mediaPath, int mediaType,
        IGemmaRuntimeCallback callback);
    void cancel(long requestId);
    void closeEngine();
    void generatePreview(long requestId, String prompt, String instruction, IGemmaRuntimeCallback callback);
    void scoreTexts(long requestId, String prompt, in String[] targets, IGemmaScoringCallback callback);
}
