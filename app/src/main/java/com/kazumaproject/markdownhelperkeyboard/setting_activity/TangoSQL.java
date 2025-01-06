package com.kazumaproject.markdownhelperkeyboard.setting_activity;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class TangoSQL extends SQLiteOpenHelper {


    private static final int DATABASE_VERSION = 1;

    private static final String DATABASE_NAME = "TangoDicDB.db";
    private static final String TABLE_NAME = "tangoDictb";
    private static final String _ID = "_id";
    private static final String COLUMN_NAME_TANGO = "tango";
    private static final String COLUMN_NAME_KANA = "kana";
    //private static final String COLUMN_NAME_PASS = "pass";

    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    _ID + " INTEGER PRIMARY KEY," +
                    COLUMN_NAME_TANGO + " TEXT," +
                    COLUMN_NAME_KANA + " TEXT)";
                    //COLUMN_NAME_PASS + " TEXT)";

    private static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + TABLE_NAME;

    TangoSQL(Context context) { super(context, DATABASE_NAME, null, DATABASE_VERSION); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(

                SQL_CREATE_ENTRIES
        );

        System.out.println("単語SQL");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion , int newVersion) {
        db.execSQL(
                SQL_DELETE_ENTRIES
        );
        onCreate(db);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion , int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}
