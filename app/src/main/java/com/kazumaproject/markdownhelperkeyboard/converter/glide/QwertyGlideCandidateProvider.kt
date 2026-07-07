package com.kazumaproject.markdownhelperkeyboard.converter.glide

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.qwerty_keyboard.glide.QwertyInputPointers
import com.kazumaproject.qwerty_keyboard.glide.QwertyKeyboardProximityInfo

interface QwertyGlideCandidateProvider {
    suspend fun getGlideCandidates(
        inputPointers: QwertyInputPointers,
        proximityInfo: QwertyKeyboardProximityInfo,
        previousText: String,
        limit: Int = 12
    ): List<Candidate>
}
