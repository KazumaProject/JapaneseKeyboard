package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.kazumaproject.markdownhelperkeyboard.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // viewの取得
        val btnStart :Button = findViewById(R.id.btnStart)//パスワード入力の所
        //ボタンを押したら次の画面へ
        btnStart.setOnClickListener {
            val intent = Intent(this,WelcomeActivity::class.java)
            startActivity(intent)
            finish()
        }

        // viewの取得
        val btnReset :Button = findViewById(R.id.btnReset)//忘れた方はこちらボタン

        //ボタンを押したら次の画面へ
        btnReset.setOnClickListener {
            val intent = Intent(this, ResetActivity1::class.java)
            startActivity(intent)
            finish()
        }

        // viewの取得
        val btnNewset :Button = findViewById(R.id.btnNewset)//パスワード入力の所

        //ボタンを押したら次の画面へ
        btnNewset.setOnClickListener {
            val intent = Intent(this,NewActivity1::class.java)
            startActivity(intent)
        }
    }
}