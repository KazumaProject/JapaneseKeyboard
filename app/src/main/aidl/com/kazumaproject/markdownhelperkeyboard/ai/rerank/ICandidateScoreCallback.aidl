package com.kazumaproject.markdownhelperkeyboard.ai.rerank;

oneway interface ICandidateScoreCallback {
    void completed(in float[] scores);
    void failed();
}
