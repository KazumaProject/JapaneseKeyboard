package com.kazumaproject.markdownhelperkeyboard.gemma.runtime;

import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.IGemmaRuntimeCallback;

oneway interface IGemmaRuntime {
    void initialize(String modelPath, String backend, int modalityFlags,
        IGemmaRuntimeCallback callback);
    void initializeModel(String modelPath, String backend, String projectorPath, IGemmaRuntimeCallback callback);
    void inspect(long requestId, String modelPath, String format, IGemmaRuntimeCallback callback);
    void generate(long requestId, String prompt, String mediaPath, int mediaType,
        IGemmaRuntimeCallback callback);
    void cancel(long requestId);
    void closeEngine();
    void generatePreview(long requestId, String prompt, String instruction, IGemmaRuntimeCallback callback);
    void generateText(long requestId, String text, String instruction, IGemmaRuntimeCallback callback);
    void generateTranslation(long requestId, String text, String instruction, String targetLanguage, IGemmaRuntimeCallback callback);
}
