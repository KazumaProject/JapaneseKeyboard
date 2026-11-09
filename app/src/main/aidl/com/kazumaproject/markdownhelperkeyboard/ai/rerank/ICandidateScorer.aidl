package com.kazumaproject.markdownhelperkeyboard.ai.rerank;

import com.kazumaproject.markdownhelperkeyboard.ai.rerank.ICandidateScoreCallback;

oneway interface ICandidateScorer {
    void score(long requestId, String modelPath, String tokenizerPath, String prefix,
        in String[] candidates, ICandidateScoreCallback callback);
    void cancel(long requestId);
}
