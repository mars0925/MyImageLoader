package com.mars.mylrucachedemo;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.ArrayList;


public class AlbumAapter extends RecyclerView.Adapter<AlbumAapter.ViewHolder> {
    private ArrayList<String> dataList;
    private static final int TAG_KEY_URI = R.id.imageloader_uri;
    private ImageLoader imageLoader;


    public AlbumAapter(Context context, ArrayList<String> dataList){
        this.dataList = dataList;
        imageLoader = new ImageLoader(context);
    }

    @NonNull
    @Override
    public AlbumAapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View contactView = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.recycleview_item, viewGroup, false);

        return new ViewHolder(contactView);
    }

    @Override
    public void onBindViewHolder(@NonNull AlbumAapter.ViewHolder viewHolder, int i) {
        ImageView i_pic = viewHolder.i_pic;
        String url = dataList.get(i);
        i_pic.setTag(TAG_KEY_URI,url);

        /*呼叫ImageLoader 的異步下載*/
        imageLoader.bindBitmap(url, i_pic, i_pic.getWidth(), i_pic.getHeight());
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView i_pic;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            i_pic = itemView.findViewById(R.id.i_pic);
        }
    }
}
