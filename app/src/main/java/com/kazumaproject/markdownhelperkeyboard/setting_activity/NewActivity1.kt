package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import com.kazumaproject.markdownhelperkeyboard.R

class NewActivity1 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new1)

        // viewの取得
        val toNewActivity2 : Button = findViewById(R.id.toNewActivity2)//パスワード入力の所
        val etNew1 : EditText = findViewById(R.id.etNew1)
        val etNew2 : EditText = findViewById(R.id.etNew2)

        //ボタンを押したら次の画面へ
        toNewActivity2.setOnClickListener {
            val intent = Intent(this, NewActivity2::class.java)
            intent.putExtra("My_Mail",etNew1.text.toString())
            intent.putExtra("My_Password",etNew2.text.toString())
            startActivity(intent)
        }

        // viewの取得
        val backMainActivity : Button = findViewById(R.id.backMainActivity)//パスワード入力の所

        //ボタンを押したら次の画面へ
        backMainActivity.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }
}