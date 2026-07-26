package com.kazumaproject.markdownhelperkeyboard.gemma.runtime;

oneway interface IGemmaRuntimeCallback {
    void onStateChanged(String state, String detail);
    void onResult(long requestId, String result);
    void onError(long requestId, String message);
}
