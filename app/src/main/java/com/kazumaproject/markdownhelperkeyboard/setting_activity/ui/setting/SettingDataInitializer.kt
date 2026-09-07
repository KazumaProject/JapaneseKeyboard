package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import com.kazumaproject.markdownhelperkeyboard.repository.RomajiMapRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingDataInitializer @Inject constructor(
    private val appPreference: AppPreference,
    private val romajiMapRepository: Lazy<RomajiMapRepository>,
    private val userDictionaryRepository: Lazy<UserDictionaryRepository>,
) {
    private val initializationMutex = Mutex()

    suspend fun initializeIfNeeded() = withContext(Dispatchers.IO) {
        initializationMutex.withLock {
            if (appPreference.romaji_map_data_version != 0) return@withLock

            romajiMapRepository.get().updateDefaultMap()
            userDictionaryRepository.get().apply {
                if (searchByReadingExactMatchSuspend("びゃんびゃんめん").isEmpty()) {
                    insert(
                        UserWord(
                            reading = "びゃんびゃんめん",
                            word = "\uD883\uDEDE\uD883\uDEDE麺",
                            posIndex = 0,
                            posScore = 4000,
                        )
                    )
                }
                if (searchByReadingExactMatchSuspend("びゃん").isEmpty()) {
                    insert(
                        UserWord(
                            reading = "びゃん",
                            word = "\uD883\uDEDE",
                            posIndex = 0,
                            posScore = 3000,
                        )
                    )
                }
            }

            appPreference.romaji_map_data_version = 1
        }
    }
}
