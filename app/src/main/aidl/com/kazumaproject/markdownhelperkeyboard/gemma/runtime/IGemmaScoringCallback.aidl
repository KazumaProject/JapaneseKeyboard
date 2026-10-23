package com.kazumaproject.markdownhelperkeyboard.gemma.runtime;

oneway interface IGemmaScoringCallback {
    void onScores(long requestId, in float[] logLikelihoods, in int[] tokenCounts);
    void onError(long requestId, String message);
}
