package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.kazumaproject.markdownhelperkeyboard.R

class DeleteActivity1 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_delete1)

        // viewの取得
        val toDeleteActivity2 : Button = findViewById(R.id.toDeleteActivity2)//パスワード入力の所

        //ボタンを押したら次の画面へ
        toDeleteActivity2.setOnClickListener {
            val intent = Intent(this,DeleteActivity2::class.java)
            startActivity(intent)
        }

        // viewの取得
        val backWelcome : Button = findViewById(R.id.backWelcome)//パスワード入力の所

        //ボタンを押したら次の画面へ
        backWelcome.setOnClickListener {
            val intent = Intent(this,WelcomeActivity::class.java)
            startActivity(intent)
        }
    }
}