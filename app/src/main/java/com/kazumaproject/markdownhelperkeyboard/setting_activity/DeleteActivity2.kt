package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.kazumaproject.markdownhelperkeyboard.R

class DeleteActivity2 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_delete2)

        // viewの取得
        val toDeleteActivity3 : Button = findViewById(R.id.toDeleteActivity3)//パスワード入力の所

        //ボタンを押したら次の画面へ
        toDeleteActivity3.setOnClickListener {
            val intent = Intent(this,DeleteActivity3::class.java)
            startActivity(intent)
        }

        // viewの取得
        val backDeleteActivity1 : Button = findViewById(R.id.backDeleteActivity1)//パスワード入力の所

        //ボタンを押したら次の画面へ
        backDeleteActivity1.setOnClickListener {
            val intent = Intent(this,DeleteActivity1::class.java)
            startActivity(intent)
        }
    }
}