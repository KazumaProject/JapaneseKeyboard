package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.kazumaproject.markdownhelperkeyboard.R

class AddActivity3 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add3)

        // viewの取得
        val toWelcome: Button = findViewById(R.id.toWelcome)//パスワード入力の所

        //ボタンを押したら次の画面へ
        toWelcome.setOnClickListener {
            val intent = Intent(this, WelcomeActivity::class.java)
            startActivity(intent)
        }

        // viewの取得
        val toDB: Button = findViewById(R.id.toDB)

        //ボタンを押したら次の画面へ
        toDB.setOnClickListener {
            val intent = Intent(this, DatabaseActivity::class.java)
            startActivity(intent)
        }
    }
}