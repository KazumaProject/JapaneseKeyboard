package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import com.kazumaproject.markdownhelperkeyboard.R

class AddActivity1 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add1)

        // viewの取得
        val toAddActivity2 : Button = findViewById(R.id.toAddActivity2)//パスワード入力の所
        val et1 : EditText = findViewById(R.id.et1)

        //ボタンを押したら次の画面へ
        toAddActivity2.setOnClickListener {
            val intent = Intent(this, AddActivity2::class.java)
            intent.putExtra("My_name",et1.text.toString())
            startActivity(intent)
        }

        // viewの取得
        val backWelcome : Button = findViewById(R.id.backWelcome)//パスワード入力の所

        //ボタンを押したら次の画面へ
        backWelcome.setOnClickListener {
            val intent = Intent(this, WelcomeActivity::class.java)
            startActivity(intent)
        }
    }
}