package com.kazumaproject.markdownhelperkeyboard.zenz.runtime;

oneway interface IZenzRuntimeCallback {
    void onReady(long requestId, int processId);
    void onStringResult(long requestId, String result);
    void onScoresResult(long requestId, in float[] scores);
    void onError(long requestId, String message);
}
