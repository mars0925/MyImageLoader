package com.mars.mylrucachedemo;

import android.graphics.Bitmap;
import android.widget.ImageView;

/**
 * Created by mars0925 on 2019/04/30.
 */

public class LoaderResult {

    ImageView imageView;
    String uri;
    Bitmap bitmap;

    public LoaderResult(ImageView imageView, String uri, Bitmap bitmap) {
         this.imageView = imageView;
         this.uri = uri;
         this.bitmap = bitmap;
    }
}
