package com.kazumaproject.markdownhelperkeyboard.zenz.runtime;

import com.kazumaproject.markdownhelperkeyboard.zenz.runtime.IZenzRuntimeCallback;

oneway interface IZenzRuntime {
    void initialize(long requestId, String modelPath, int nCtx, int nThreads,
        IZenzRuntimeCallback callback);
    void generate(long requestId, String profile, String topic, String style,
        String preference, String leftContext, String rightContext, String input,
        int maxTokens, IZenzRuntimeCallback callback);
    void evaluate(long requestId, String profile, String topic, String style,
        String preference, String leftContext, String rightContext, String input,
        String candidate, IZenzRuntimeCallback callback);
    void score(long requestId, String profile, String topic, String style,
        String preference, String leftContext, String rightContext, String input,
        in String[] candidates, IZenzRuntimeCallback callback);
    void cancel(long requestId);
    void closeEngine();
}
