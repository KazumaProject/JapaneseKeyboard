package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.kazumaproject.markdownhelperkeyboard.R

class ResetActivity3 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset3)

        // viewの取得
        val toResetActivity4 : Button = findViewById(R.id.toResetActivity4)//パスワード入力の所

        //ボタンを押したら次の画面へ
        toResetActivity4.setOnClickListener {
            val intent = Intent(this, ResetActivity4::class.java)
            startActivity(intent)
        }

        // viewの取得
        val backResetActivity2 : Button = findViewById(R.id.backResetActivity2)//パスワード入力の所

        //ボタンを押したら次の画面へ
        backResetActivity2.setOnClickListener {
            val intent = Intent(this, ResetActivity2::class.java)
            startActivity(intent)
        }
    }
}