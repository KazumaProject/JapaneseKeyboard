package com.kazumaproject.markdownhelperkeyboard.learning.repository

import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import javax.inject.Inject

class LearnRepository @Inject constructor(
    private val learnDao: LearnDao
) {
    suspend fun insertOrMoveLearnedData(learnData: LearnEntity) {
        // Check if the input and out already exist
        val exists = learnDao.exists(learnData.input, learnData.out) > 0

        if (exists) {
            // Check if this is the first entry
            val firstId = learnDao.getFirstId(learnData.input)

            if (firstId != null && firstId == learnData.id) {
                // If the out is already first, do nothing
                return
            } else {
                // Move the out one step forward
                learnDao.moveOutToPrevious(learnData.input, learnData.out)
                return
            }
        }

        // Insert the new data if it doesn't exist
        learnDao.insert(learnData)

        // Clean up any invalid IDs caused by the move operation
        learnDao.cleanUpInvalidIds()
    }

    suspend fun insertLearnedData(learnData: LearnEntity) {
        learnDao.insert(learnData)
    }
    suspend fun getLearnedDataByInput(input: String): List<String> {
        return learnDao.getByInput(input)
    }
}