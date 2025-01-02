package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.kazumaproject.markdownhelperkeyboard.R

class ResetActivity2 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset2)

        // viewの取得
        val toResetActivity3 : Button = findViewById(R.id.toResetActivity3)//パスワード入力の所

        //ボタンを押したら次の画面へ
        toResetActivity3.setOnClickListener {
            val intent = Intent(this, ResetActivity3::class.java)
            startActivity(intent)
        }

        // viewの取得
        val backResetActivity1 : Button = findViewById(R.id.backResetActivity1)//パスワード入力の所

        //ボタンを押したら次の画面へ
        backResetActivity1.setOnClickListener {
            val intent = Intent(this, ResetActivity1::class.java)
            startActivity(intent)
        }
    }
}