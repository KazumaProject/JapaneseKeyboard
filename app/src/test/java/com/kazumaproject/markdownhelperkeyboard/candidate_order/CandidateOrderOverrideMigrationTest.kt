package com.kazumaproject.markdownhelperkeyboard.candidate_order

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.candidate_order.model.CandidateOrderScope
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CandidateOrderOverrideMigrationTest {
    @Test
    fun migration43To44PreservesExistingRulesAsExactAndAllowsLexicalRule() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "candidate-order-scope-migration-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE candidate_order_override (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                input TEXT NOT NULL,
                                candidate TEXT NOT NULL,
                                rank INTEGER NOT NULL,
                                createdAt INTEGER NOT NULL,
                                updatedAt INTEGER NOT NULL
                            )
                            """.trimIndent(),
                        )
                        db.execSQL(
                            "CREATE INDEX index_candidate_order_override_input ON candidate_order_override (input)",
                        )
                        db.execSQL(
                            "CREATE UNIQUE INDEX index_candidate_order_override_input_candidate ON candidate_order_override (input, candidate)",
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build(),
        )
        try {
            val db = helper.writableDatabase
            db.execSQL(
                "INSERT INTO candidate_order_override (input, candidate, rank, createdAt, updatedAt) VALUES ('ひ', '火', 1, 1, 1)",
            )

            AppDatabase.MIGRATION_43_44.migrate(db)

            db.query(
                "SELECT input, scope, candidate, rank FROM candidate_order_override",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("ひ", cursor.getString(0))
                assertEquals(CandidateOrderScope.EXACT_INPUT.name, cursor.getString(1))
                assertEquals("火", cursor.getString(2))
                assertEquals(1, cursor.getInt(3))
            }

            db.execSQL(
                "INSERT INTO candidate_order_override (input, scope, candidate, rank, createdAt, updatedAt) VALUES ('ひ', 'LEXICAL_UNIT', '火', 1, 2, 2)",
            )
            db.query(
                "SELECT COUNT(*) FROM candidate_order_override WHERE input = 'ひ' AND candidate = '火'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(2, cursor.getInt(0))
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}
