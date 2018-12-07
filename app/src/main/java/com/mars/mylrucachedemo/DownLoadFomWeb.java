package com.mars.mylrucachedemo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static android.content.ContentValues.TAG;

/**
 * 網路下載
 */
public class DownLoadFomWeb {
    private static final int IO_BUFFER_SIZE = 8 * 1024;
    private static final int MESSAGE_POST_RESULT = 1;
    private static final int TAG_KEY_URI = R.id.imageloader_uri;

    public static void downLoadBitmapFromWeb(String uri, ImageView img) {
        new Thread(new DownloadRunnable(uri, img)).start();
    }

    static class DownloadRunnable implements Runnable {
        private String url;
        private ImageView img;

        DownloadRunnable(String url, ImageView img) {
            this.url = url;
            this.img = img;
        }

        @Override
        public void run() {
            Bitmap bitmap = downLoadBitmapFromUrl(url);

            if (bitmap != null) {
                LoaderResult result = new LoaderResult(img, url, bitmap);
                Message message = mMainHandler.obtainMessage(MESSAGE_POST_RESULT, result);
                message.sendToTarget();
            }
        }
    }

    private static Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            LoaderResult result = (LoaderResult) msg.obj;
            ImageView imageView = result.imageView;
            imageView.setImageBitmap(result.bitmap);
            String uri = (String) imageView.getTag(TAG_KEY_URI);
            if(uri.equals(result.uri)){
                imageView.setImageBitmap(result.bitmap);
            }else {
                Log.d(TAG, "set image bitmap,but url has changed,ignored!");
            }
        }
    };


    private static Bitmap downLoadBitmapFromUrl(String uri) {
        Bitmap bitmap = null;
        HttpURLConnection urlConnection = null;
        BufferedInputStream in = null;
        try {
            URL url = new URL(uri);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream(), IO_BUFFER_SIZE);
            bitmap = BitmapFactory.decodeStream(in);
        } catch (IOException e) {
            Log.e(TAG, "Error in downloadBitmap:" + e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            close(in);
        }
        return bitmap;
    }

    private static void close(Closeable closeable) {

        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
