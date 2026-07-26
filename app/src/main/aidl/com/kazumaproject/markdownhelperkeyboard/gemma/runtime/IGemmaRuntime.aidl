package com.kazumaproject.markdownhelperkeyboard.gemma.runtime;

import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.IGemmaRuntimeCallback;

oneway interface IGemmaRuntime {
    void initialize(String modelPath, String backend, int modalityFlags,
        IGemmaRuntimeCallback callback);
    void generate(long requestId, String prompt, String mediaPath, int mediaType,
        IGemmaRuntimeCallback callback);
    void cancel(long requestId);
    void closeEngine();
}
