package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.kazumaproject.markdownhelperkeyboard.R

class WelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        // viewの取得
        val toAddActivity1 : Button = findViewById(R.id.toAddActivity1)//パスワード入力の所

        //ボタンを押したら次の画面へ
        toAddActivity1.setOnClickListener {
            val intent = Intent(this,AddActivity1::class.java)
            startActivity(intent)
            finish()
        }

        // viewの取得
        val toDeleteActivity1 : Button = findViewById(R.id.toDeleteActivity1)//パスワード入力の所

        //ボタンを押したら次の画面へ
        toDeleteActivity1.setOnClickListener {
            val intent = Intent(this,DeleteActivity1::class.java)
            startActivity(intent)
            finish()
        }

        // viewの取得
        val backMainActivity : Button = findViewById(R.id.backMainActivity)//パスワード入力の所

        //ボタンを押したら次の画面へ
        backMainActivity.setOnClickListener {
            val intent = Intent(this,MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}