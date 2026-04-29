package com.kazumaproject.markdownhelperkeyboard.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kazumaproject.data.clicked_symbol.ClickedSymbol
import com.kazumaproject.markdownhelperkeyboard.clicked_symbol.database.ClickedSymbolDao
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ClipboardHistoryDao
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ClipboardHistoryItem
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ItemTypeConverter
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CircularFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.LongPressFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TfbiFlickDirectionConverter
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepLongPressMappingEntity
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database.KeyboardLayoutDao
import com.kazumaproject.markdownhelperkeyboard.custom_romaji.database.MapTypeConverter
import com.kazumaproject.markdownhelperkeyboard.custom_romaji.database.RomajiMapDao
import com.kazumaproject.markdownhelperkeyboard.custom_romaji.database.RomajiMapEntity
import com.kazumaproject.markdownhelperkeyboard.delete_key_flick.database.DeleteKeyFlickDeleteTarget
import com.kazumaproject.markdownhelperkeyboard.delete_key_flick.database.DeleteKeyFlickDeleteTargetDao
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaPromptTemplate
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaPromptTemplateDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.ngram_rule.database.NgramRuleDao
import com.kazumaproject.markdownhelperkeyboard.ngram_rule.database.ThreeNodeRuleEntity
import com.kazumaproject.markdownhelperkeyboard.ngram_rule.database.TwoNodeRuleEntity
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWord
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWordDao
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.database.PhysicalKeyboardShortcutDao
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.database.PhysicalKeyboardShortcutItem
import com.kazumaproject.markdownhelperkeyboard.short_cut.data.ShortcutItem
import com.kazumaproject.markdownhelperkeyboard.short_cut.database.ShortcutDao
import com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.database.SystemUserDictionaryDao
import com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.database.SystemUserDictionaryEntry
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWordDao
import com.kazumaproject.markdownhelperkeyboard.user_template.database.UserTemplate
import com.kazumaproject.markdownhelperkeyboard.user_template.database.UserTemplateDao

@Database(
    entities = [
        LearnEntity::class,
        ClickedSymbol::class,
        UserWord::class,
        CustomKeyboardLayout::class,
        KeyDefinition::class,
        FlickMapping::class,
        CircularFlickMapping::class,
        TwoStepFlickMapping::class,
        LongPressFlickMapping::class,
        TwoStepLongPressMappingEntity::class,
        UserTemplate::class,
        ClipboardHistoryItem::class,
        RomajiMapEntity::class,
        NgWord::class,
        ShortcutItem::class,
        SystemUserDictionaryEntry::class,
        TwoNodeRuleEntity::class,
        ThreeNodeRuleEntity::class,
        GemmaPromptTemplate::class,
        DeleteKeyFlickDeleteTarget::class,
        PhysicalKeyboardShortcutItem::class,
    ],
    version = 26,
    exportSchema = false
)
@TypeConverters(
    ItemTypeConverter::class,
    MapTypeConverter::class,
    TfbiFlickDirectionConverter::class
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun learnDao(): LearnDao
    abstract fun clickedSymbolDao(): ClickedSymbolDao
    abstract fun userWordDao(): UserWordDao
    abstract fun keyboardLayoutDao(): KeyboardLayoutDao
    abstract fun userTemplateDao(): UserTemplateDao
    abstract fun clipboardHistoryDao(): ClipboardHistoryDao
    abstract fun romajiMapDao(): RomajiMapDao
    abstract fun ngWordDao(): NgWordDao
    abstract fun shortcutDao(): ShortcutDao
    abstract fun systemUserDictionaryDao(): SystemUserDictionaryDao
    abstract fun ngramRuleDao(): NgramRuleDao
    abstract fun gemmaPromptTemplateDao(): GemmaPromptTemplateDao
    abstract fun deleteKeyFlickDeleteTargetDao(): DeleteKeyFlickDeleteTargetDao
    abstract fun physicalKeyboardShortcutDao(): PhysicalKeyboardShortcutDao

    companion object {

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `clicked_symbol_history` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `mode` TEXT NOT NULL,
                      `symbol` TEXT NOT NULL,
                      `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_word` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `word` TEXT NOT NULL,
                      `reading` TEXT NOT NULL,
                      `posIndex` INTEGER NOT NULL,
                      `posScore` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * The new migration from version 3 to 4.
         * This migration adds an index to the `reading` column of the `user_word` table
         * to significantly improve query performance for prefix searches.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_word_reading` ON `user_word`(`reading`)")
            }
        }

        /**
         * バージョン4から5へのマイグレーション。
         * ユーザーによるキーボードレイアウトのカスタマイズ機能をサポートするため、
         * 3つの新しいテーブル (keyboard_layouts, key_definitions, flick_mappings) を追加します。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `keyboard_layouts` (
                        `layoutId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `columnCount` INTEGER NOT NULL, 
                        `rowCount` INTEGER NOT NULL, 
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `key_definitions` (
                        `keyId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `ownerLayoutId` INTEGER NOT NULL, 
                        `label` TEXT NOT NULL, 
                        `row` INTEGER NOT NULL, 
                        `column` INTEGER NOT NULL, 
                        `rowSpan` INTEGER NOT NULL, 
                        `colSpan` INTEGER NOT NULL, 
                        `keyType` TEXT NOT NULL, 
                        `isSpecialKey` INTEGER NOT NULL, 
                        `drawableResId` INTEGER, 
                        `keyIdentifier` TEXT NOT NULL, 
                        FOREIGN KEY(`ownerLayoutId`) REFERENCES `keyboard_layouts`(`layoutId`) ON DELETE CASCADE ON UPDATE NO ACTION
                    )
                """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_key_definitions_ownerLayoutId` ON `key_definitions`(`ownerLayoutId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `flick_mappings` (
                        `ownerKeyId` INTEGER NOT NULL, 
                        `stateIndex` INTEGER NOT NULL, 
                        `flickDirection` TEXT NOT NULL, 
                        `actionType` TEXT NOT NULL, 
                        `actionValue` TEXT, 
                        PRIMARY KEY(`ownerKeyId`, `stateIndex`, `flickDirection`), 
                        FOREIGN KEY(`ownerKeyId`) REFERENCES `key_definitions`(`keyId`) ON DELETE CASCADE ON UPDATE NO ACTION
                    )
                """.trimIndent()
                )
            }
        }

        /**
         * バージョン5から6へのマイグレーション。
         * key_definitions テーブルに action 列を追加します。
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `key_definitions` ADD COLUMN `action` TEXT")
            }
        }

        /**
         * バージョン6から7へのマイグレーション。
         * 定型文機能をサポートするため、user_template テーブルを追加します。
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_template` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `word` TEXT NOT NULL,
                      `reading` TEXT NOT NULL,
                      `posIndex` INTEGER NOT NULL,
                      `posScore` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_template_reading` ON `user_template`(`reading`)")
            }
        }

        /**
         * バージョン7から8へのマイグレーション。
         * learn_table に leftId と rightId カラムを追加します。
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `learn_table` ADD COLUMN `leftId` INTEGER")
                db.execSQL("ALTER TABLE `learn_table` ADD COLUMN `rightId` INTEGER")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `clipboard_history` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `itemType` TEXT NOT NULL,
                      `textData` TEXT,
                      `imageData` BLOB,
                      `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `romaji_maps` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `mapData` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `isDeletable` INTEGER NOT NULL
                    )
                """.trimIndent()
                )
            }
        }

        /**
         * バージョン10から11へのマイグレーション。
         * keyboard_layoutsテーブルにisRomajiカラムを追加します。
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE keyboard_layouts ADD COLUMN isRomaji INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ng_word` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `yomi` TEXT NOT NULL,
                      `tango` TEXT NOT NULL
                    )
                """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_ng_word_yomi`
                    ON `ng_word`(`yomi`)
                """.trimIndent()
                )
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `shortcut_items` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `typeId` TEXT NOT NULL,
                      `sortOrder` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * バージョン13から14へのマイグレーション。
         * TWO_STEP_FLICK の永続化のために two_step_flick_mappings テーブルを追加します。
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `two_step_flick_mappings` (
                        `ownerKeyId` INTEGER NOT NULL,
                        `firstDirection` TEXT NOT NULL,
                        `secondDirection` TEXT NOT NULL,
                        `output` TEXT NOT NULL,
                        PRIMARY KEY(`ownerKeyId`, `firstDirection`, `secondDirection`),
                        FOREIGN KEY(`ownerKeyId`) REFERENCES `key_definitions`(`keyId`) ON DELETE CASCADE ON UPDATE NO ACTION
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_two_step_flick_mappings_ownerKeyId`
                    ON `two_step_flick_mappings`(`ownerKeyId`)
                    """.trimIndent()
                )
            }
        }

        /**
         * バージョン14から15へのマイグレーション。
         * user_word, learn_table, user_template の3テーブルに対し、
         * 重複データを削除した上で、複合ユニークインデックスを作成します。
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {

                // --- 1. UserWord (user_word) ---
                // 重複削除: (word, reading) が同じなら id が最小のもの以外削除
                db.execSQL(
                    """
                    DELETE FROM user_word
                    WHERE id NOT IN (
                        SELECT MIN(id)
                        FROM user_word
                        GROUP BY word, reading
                    )
                    """.trimIndent()
                )
                // ユニークインデックス作成
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_word_word_reading` ON `user_word`(`word`, `reading`)")


                // --- 2. LearnEntity (learn_table) ---
                // 重複削除: (input, out) が同じなら id が最小のもの以外削除
                db.execSQL(
                    """
                    DELETE FROM learn_table
                    WHERE id NOT IN (
                        SELECT MIN(id)
                        FROM learn_table
                        GROUP BY input, out
                    )
                    """.trimIndent()
                )
                // ユニークインデックス作成
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_learn_table_input_out` ON `learn_table`(`input`, `out`)")


                // --- 3. UserTemplate (user_template) ---
                // 重複削除: (word, reading) が同じなら id が最小のもの以外削除
                db.execSQL(
                    """
                    DELETE FROM user_template
                    WHERE id NOT IN (
                        SELECT MIN(id)
                        FROM user_template
                        GROUP BY word, reading
                    )
                    """.trimIndent()
                )
                // ユニークインデックス作成
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_template_word_reading` ON `user_template`(`word`, `reading`)")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // keyboard_layouts に sortOrder を追加（既存行は 0）
                db.execSQL("ALTER TABLE keyboard_layouts ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 旧テーブルを削除（BLOBデータが含まれており、移行中のCursorWindow制限を避けるため）
                db.execSQL("DROP TABLE IF EXISTS `clipboard_history`")
                // 新しいスキーマで再作成
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `clipboard_history` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `itemType` TEXT NOT NULL,
                      `preview` TEXT NOT NULL,
                      `contentPath` TEXT NOT NULL,
                      `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `system_user_dictionary_entry` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `yomi` TEXT NOT NULL,
                      `tango` TEXT NOT NULL,
                      `score` INTEGER NOT NULL,
                      `leftId` INTEGER NOT NULL,
                      `rightId` INTEGER NOT NULL,
                      `createdAt` INTEGER NOT NULL,
                      `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_system_user_dictionary_entry_yomi`
                    ON `system_user_dictionary_entry`(`yomi`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_system_user_dictionary_entry_tango`
                    ON `system_user_dictionary_entry`(`tango`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_system_user_dictionary_entry_yomi_tango_leftId_rightId`
                    ON `system_user_dictionary_entry`(`yomi`, `tango`, `leftId`, `rightId`)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `two_node_rule` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `prevWord` TEXT NOT NULL,
                      `prevLeftId` INTEGER NOT NULL,
                      `prevRightId` INTEGER NOT NULL,
                      `currentWord` TEXT NOT NULL,
                      `currentLeftId` INTEGER NOT NULL,
                      `currentRightId` INTEGER NOT NULL,
                      `adjustment` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_two_node_rule_prevWord_prevLeftId_prevRightId_currentWord_currentLeftId_currentRightId`
                    ON `two_node_rule`(`prevWord`, `prevLeftId`, `prevRightId`, `currentWord`, `currentLeftId`, `currentRightId`)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `three_node_rule` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `firstWord` TEXT NOT NULL,
                      `firstLeftId` INTEGER NOT NULL,
                      `firstRightId` INTEGER NOT NULL,
                      `secondWord` TEXT NOT NULL,
                      `secondLeftId` INTEGER NOT NULL,
                      `secondRightId` INTEGER NOT NULL,
                      `thirdWord` TEXT NOT NULL,
                      `thirdLeftId` INTEGER NOT NULL,
                      `thirdRightId` INTEGER NOT NULL,
                      `adjustment` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_three_node_rule_firstWord_firstLeftId_firstRightId_secondWord_secondLeftId_secondRightId_thirdWord_thirdLeftId_thirdRightId`
                    ON `three_node_rule`(`firstWord`, `firstLeftId`, `firstRightId`, `secondWord`, `secondLeftId`, `secondRightId`, `thirdWord`, `thirdLeftId`, `thirdRightId`)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `gemma_prompt_template` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `title` TEXT NOT NULL,
                      `prompt` TEXT NOT NULL,
                      `isEnabled` INTEGER NOT NULL,
                      `sortOrder` INTEGER NOT NULL,
                      `createdAt` INTEGER NOT NULL,
                      `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_gemma_prompt_template_sortOrder`
                    ON `gemma_prompt_template`(`sortOrder`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_gemma_prompt_template_isEnabled`
                    ON `gemma_prompt_template`(`isEnabled`)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `long_press_flick_mappings` (
                        `ownerKeyId` INTEGER NOT NULL,
                        `flickDirection` TEXT NOT NULL,
                        `output` TEXT NOT NULL,
                        PRIMARY KEY(`ownerKeyId`, `flickDirection`),
                        FOREIGN KEY(`ownerKeyId`) REFERENCES `key_definitions`(`keyId`) ON DELETE CASCADE ON UPDATE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_long_press_flick_mappings_ownerKeyId`
                    ON `long_press_flick_mappings`(`ownerKeyId`)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `two_step_long_press_mappings` (
                        `ownerKeyId` INTEGER NOT NULL,
                        `firstDirection` TEXT NOT NULL,
                        `secondDirection` TEXT NOT NULL,
                        `output` TEXT NOT NULL,
                        PRIMARY KEY(`ownerKeyId`, `firstDirection`, `secondDirection`),
                        FOREIGN KEY(`ownerKeyId`) REFERENCES `key_definitions`(`keyId`) ON DELETE CASCADE ON UPDATE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_two_step_long_press_mappings_ownerKeyId`
                    ON `two_step_long_press_mappings`(`ownerKeyId`)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_two_step_long_press_mappings_ownerKeyId`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `two_step_long_press_mappings_new` (
                        `ownerKeyId` INTEGER NOT NULL,
                        `firstDirection` TEXT NOT NULL,
                        `secondDirection` TEXT NOT NULL,
                        `output` TEXT NOT NULL,
                        PRIMARY KEY(`ownerKeyId`, `firstDirection`, `secondDirection`),
                        FOREIGN KEY(`ownerKeyId`) REFERENCES `key_definitions`(`keyId`) ON DELETE CASCADE ON UPDATE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `two_step_long_press_mappings_new` (
                        `ownerKeyId`,
                        `firstDirection`,
                        `secondDirection`,
                        `output`
                    )
                    SELECT
                        `ownerKeyId`,
                        `firstDirection`,
                        `secondDirection`,
                        `output`
                    FROM `two_step_long_press_mappings`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `two_step_long_press_mappings`")
                db.execSQL(
                    "ALTER TABLE `two_step_long_press_mappings_new` RENAME TO `two_step_long_press_mappings`"
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_two_step_long_press_mappings_ownerKeyId`
                    ON `two_step_long_press_mappings`(`ownerKeyId`)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `clipboard_history` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `delete_key_flick_delete_targets` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `symbol` TEXT NOT NULL,
                      `sortOrder` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_delete_key_flick_delete_targets_symbol`
                    ON `delete_key_flick_delete_targets` (`symbol`)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `physical_keyboard_shortcut_items` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `context` TEXT NOT NULL,
                      `keyCode` INTEGER NOT NULL,
                      `scanCode` INTEGER,
                      `ctrl` INTEGER NOT NULL,
                      `shift` INTEGER NOT NULL,
                      `alt` INTEGER NOT NULL,
                      `meta` INTEGER NOT NULL,
                      `actionId` TEXT NOT NULL,
                      `enabled` INTEGER NOT NULL,
                      `sortOrder` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_physical_keyboard_shortcut_items_context_keyCode_scanCode_ctrl_shift_alt_meta`
                    ON `physical_keyboard_shortcut_items` (`context`, `keyCode`, `scanCode`, `ctrl`, `shift`, `alt`, `meta`)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `circular_flick_mappings` (
                        `ownerKeyId` INTEGER NOT NULL,
                        `stateIndex` INTEGER NOT NULL,
                        `circularDirection` TEXT NOT NULL,
                        `actionType` TEXT NOT NULL,
                        `actionValue` TEXT,
                        PRIMARY KEY(`ownerKeyId`, `stateIndex`, `circularDirection`),
                        FOREIGN KEY(`ownerKeyId`) REFERENCES `key_definitions`(`keyId`) ON DELETE CASCADE ON UPDATE NO ACTION
                    )
                    """.trimIndent()
                )
            }
        }

    }
}
