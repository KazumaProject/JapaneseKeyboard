package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.kazumaproject.markdownhelperkeyboard.R

class DeleteActivity3 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_delete3)

        // viewの取得
        val toDeleteActivity4 : Button = findViewById(R.id.toDeleteActivity4)//パスワード入力の所

        //ボタンを押したら次の画面へ
        toDeleteActivity4.setOnClickListener {
            val intent = Intent(this,DeleteActivity4::class.java)
            startActivity(intent)
        }

        // viewの取得
        val backDeleteActivity2 : Button = findViewById(R.id.backDeleteActivity2)//パスワード入力の所

        //ボタンを押したら次の画面へ
        backDeleteActivity2.setOnClickListener {
            val intent = Intent(this,DeleteActivity2::class.java)
            startActivity(intent)
        }
    }
}