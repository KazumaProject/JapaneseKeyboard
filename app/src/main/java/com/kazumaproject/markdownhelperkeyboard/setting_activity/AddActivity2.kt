package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.kazumaproject.markdownhelperkeyboard.R

class AddActivity2 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add2)

        val tvName : TextView = findViewById(R.id.tvName)
        //値を受け取って表示
        val myName = intent.getStringExtra("My_name")
        tvName.text = myName

        // viewの取得
        val toAddActivity3 : Button = findViewById(R.id.toAddActivity3)//パスワード入力の所

        //ボタンを押したら次の画面へ
        toAddActivity3.setOnClickListener {
            val intent = Intent(this, AddActivity3::class.java)
            startActivity(intent)
        }

        // viewの取得
        val backAddActivity1: Button = findViewById(R.id.backAddActivity1)//パスワード入力の所

        //ボタンを押したら次の画面へ
        backAddActivity1.setOnClickListener {
            val intent = Intent(this, AddActivity1::class.java)
            startActivity(intent)
        }
    }
}