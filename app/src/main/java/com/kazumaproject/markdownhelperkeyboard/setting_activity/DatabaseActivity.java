package com.kazumaproject.markdownhelperkeyboard.setting_activity;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.kazumaproject.markdownhelperkeyboard.R;

public class DatabaseActivity extends AppCompatActivity {

    ListView myListView;

    //起動時
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.database);

        //リストビュー
        myListView = findViewById(R.id.listView);

        //db
        MyOpenHelper myOpenHelper = new MyOpenHelper(this);
        SQLiteDatabase db = myOpenHelper.getWritableDatabase();

        //select
        Cursor c = db.rawQuery("select * from myPasstb" , null);

        //adapterの準備
        //表示するカラム名
        String[] from = {"_id","name"};

        //バインドするViewリソース
        int[] to = {android.R.id.text1,android.R.id.text2};

        SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,android.R.layout.simple_list_item_2,c,from,to,0);

        //バインドして表示
        myListView.setAdapter(adapter);

        myListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //各要素を取得
                // _id
                String s1 = ((TextView)view.findViewById(android.R.id.text1)).getText().toString();
                //name
                //String s2 = ((TextView)view.findViewById(android.R.id.text2)).getText().toString();

                //参照・更新へ
                Intent intent = new Intent(getApplication(),Setting.class);

                //モード指定 _idを渡す
                intent.putExtra("KBN",s1);

                //行く
                startActivity(intent);
            }
        });

    }

    //リターン時
    @Override
    protected void onRestart(){
        super.onRestart();
        reload();
    }


    //登録ボタンを押したときは新規登録へ画面を遷移
    public void Register(View view) {
        Intent intent = new Intent(getApplication(),Setting.class);

        //モード指定　空は新規
        intent.putExtra("KBN","");

        //行く
        startActivity(intent);
    }

    public  void  reload()
    {
        Intent intent = getIntent();
        overridePendingTransition(0,0);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        finish();
        overridePendingTransition(0,0);
        startActivity(intent);

    }
}