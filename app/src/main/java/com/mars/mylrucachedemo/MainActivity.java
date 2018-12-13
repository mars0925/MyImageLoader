package com.mars.mylrucachedemo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private RecyclerView r_album;
    private GridLayoutManager grid;
    private AlbumAapter adapter;
    private ArrayList<String> dataList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initData();
        setContentView(R.layout.activity_main);
        r_album = findViewById(R.id.r_album);

        grid = new GridLayoutManager(this,3);
        adapter = new AlbumAapter(this,dataList);

        r_album.setLayoutManager(grid);
        r_album.setAdapter(adapter);
    }

    /*網路圖片資料來源*/
    private void initData() {
        dataList = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            dataList.add("https://cdn.pixabay.com/photo/2018/12/02/21/47/pair-3852277_960_720.jpg");
        }
    }
}
