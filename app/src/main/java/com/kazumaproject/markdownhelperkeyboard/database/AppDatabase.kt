package com.kazumaproject.markdownhelperkeyboard.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kazumaproject.data.clicked_symbol.ClickedSymbol
import com.kazumaproject.markdownhelperkeyboard.clicked_symbol.database.ClickedSymbolDao
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.BitmapConverter
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ClipboardHistoryDao
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ClipboardHistoryItem
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ItemTypeConverter
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TfbiFlickDirectionConverter
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database.KeyboardLayoutDao
import com.kazumaproject.markdownhelperkeyboard.custom_romaji.database.MapTypeConverter
import com.kazumaproject.markdownhelperkeyboard.custom_romaji.database.RomajiMapDao
import com.kazumaproject.markdownhelperkeyboard.custom_romaji.database.RomajiMapEntity
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWord
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWordDao
import com.kazumaproject.markdownhelperkeyboard.short_cut.data.ShortcutItem
import com.kazumaproject.markdownhelperkeyboard.short_cut.database.ShortcutDao
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
        TwoStepFlickMapping::class,
        UserTemplate::class,
        ClipboardHistoryItem::class,
        RomajiMapEntity::class,
        NgWord::class,
        ShortcutItem::class
    ],
    version = 16,
    exportSchema = false
)
@TypeConverters(
    BitmapConverter::class,
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

    }
}
