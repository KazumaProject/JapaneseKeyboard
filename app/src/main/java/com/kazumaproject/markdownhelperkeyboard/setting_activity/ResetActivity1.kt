package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import com.kazumaproject.markdownhelperkeyboard.R

class ResetActivity1 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset1)

        // viewの取得
        val toResetActivity2 :Button = findViewById(R.id.toResetActivity2)//パスワード入力の所

        //ボタンを押したら次の画面へ
        toResetActivity2.setOnClickListener {
            val intent = Intent(this,ResetActivity2::class.java)
            startActivity(intent)
        }

        // viewの取得
        val backMainActivity :Button = findViewById(R.id.backMainActivity)//パスワード入力の所

        //ボタンを押したら次の画面へ
        backMainActivity.setOnClickListener {
            val intent = Intent(this,MainActivity::class.java)
            startActivity(intent)
        }
    }
}