package com.kazumaproject.markdownhelperkeyboard.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kazumaproject.data.clicked_symbol.ClickedSymbol
import com.kazumaproject.markdownhelperkeyboard.clicked_symbol.database.ClickedSymbolDao
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database.KeyboardLayoutDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
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
        UserTemplate::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun learnDao(): LearnDao
    abstract fun clickedSymbolDao(): ClickedSymbolDao
    abstract fun userWordDao(): UserWordDao
    abstract fun keyboardLayoutDao(): KeyboardLayoutDao
    abstract fun userTemplateDao(): UserTemplateDao

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
                // SQL command to create an index on the 'reading' column
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
                // 1. keyboard_layouts テーブルの作成
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

                // 2. key_definitions テーブルの作成
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
                // key_definitions テーブルのインデックス作成 (外部キーのパフォーマンス向上)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_key_definitions_ownerLayoutId` ON `key_definitions`(`ownerLayoutId`)")

                // 3. flick_mappings テーブルの作成
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
         * この列は、キーが持つ特別な機能（削除、スペースなど）を文字列として保存します。
         */
        val MIGRATION_5_6 = object : Migration(5, 6) { // <<< ★★★ ここから追加 ★★★
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `key_definitions` ADD COLUMN `action` TEXT")
            }
        }

        /**
         * バージョン6から7へのマイグレーション。
         * 定型文機能をサポートするため、user_template テーブルを追加します。
         * 検索パフォーマンス向上のため、reading列にインデックスも作成します。
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // user_template テーブルの作成
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
                // reading 列にインデックスを作成
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_template_reading` ON `user_template`(`reading`)")
            }
        }


    }
}
