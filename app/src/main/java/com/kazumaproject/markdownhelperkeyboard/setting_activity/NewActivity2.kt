package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.kazumaproject.markdownhelperkeyboard.R

class NewActivity2 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new2)

        val tvNewMail1 : TextView = findViewById(R.id.tvNewMail1)
        val tvNewMail2 : TextView = findViewById(R.id.tvNewMail2)

        //値を受け取って表示
        val myMail = intent.getStringExtra("My_Mail")
        val myPass = intent.getStringExtra("My_Password")
        tvNewMail1.text = myMail
        tvNewMail2.text = myPass

        val toNewActivity3 : Button = findViewById(R.id.toNewActivity3)//パスワード入力の所

        //ボタンを押したら次の画面へ
        toNewActivity3.setOnClickListener {
            val intent = Intent(this,NewActivity3::class.java)
            startActivity(intent)
        }

        // viewの取得
        val backNewActivity1 : Button = findViewById(R.id.backNewActivity1)//パスワード入力の所

        //ボタンを押したら次の画面へ
        backNewActivity1.setOnClickListener {
            val intent = Intent(this,NewActivity1::class.java)
            startActivity(intent)
        }
    }
}