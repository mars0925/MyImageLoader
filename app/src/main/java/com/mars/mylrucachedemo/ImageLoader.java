package com.mars.mylrucachedemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import com.jakewharton.disklrucache.DiskLruCache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static android.content.ContentValues.TAG;

public class ImageLoader {
    private ImageResizer mImageResizer;
    private LruCache<String, Bitmap> mMemoryCache; //内存缓存
    private DiskLruCache mDiskLruCache;//硬碟缓存
    private static final int DISK_CACHE_SIZE = 50 * 1024 * 1024;//硬碟緩存的容量
    private boolean mIsDiskLruCacheCreated;
    private static final int DISK_CACHE_INDEX = 0;//缓存個數
    private static final int IO_BUFFER_SIZE = 8 * 1024;//IO缓存流大小
    private static final int MESSAGE_POST_RESULT = 1;
    private static final int TAG_KEY_URI = R.id.imageloader_uri;

    /*線程池參數*/
    /*線程池參數*/
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();//CPU的的數量
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;//線程池初始化線程數，是CPU數量加1
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;///這是執行緒池維護的最大的執行緒數，CPU的兩倍加1
    private static final long KEEP_ALIVE = 10;//當執行緒數超過初始化的執行緒數時，多出來的空閒執行緒，存活的時間


    public ImageLoader(Context context) {
        mImageResizer = new ImageResizer();//圖片縮圖類別
        Context mContext = context;
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        /*建立緩存*/
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight() / 1024;
            }
            /*當item被回收或者刪掉時調用。該方法當value被回收釋放存儲空間時被remove調用*/
            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                super.entryRemoved(evicted, key, oldValue, newValue);
                Log.e("entryRemoved",key + "");
                Log.e("entryRemoved",key + "oldValue");
                Log.e("entryRemoved",key + "newValue");
            }
        };

        /*建立緩存檔案*/
        File diskCacheDir = getDiskCacheDir(mContext, "bitmap");
        //如果檔案不存在 直接建立
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs();
        }

        if (getUsableSpace(diskCacheDir) > DISK_CACHE_SIZE) {
            try {
                mDiskLruCache = DiskLruCache.open(diskCacheDir, 1, 1, DISK_CACHE_SIZE);
                mIsDiskLruCacheCreated = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 内存缓存添加和获取
     */
    private void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        Log.e(TAG, "addBitmapToMemoryCache: ");
        if (getBitmapFromMemoryCache(key) == null) {
            mMemoryCache.put(key, bitmap);
        }
    }

    /**
     * 内存缓存獲取 拿key到 LruCache的物件查看看有沒有圖片
     */
    private Bitmap getBitmapFromMemoryCache(String key) {
        return mMemoryCache.get(key);
    }

    /**
     * 硬碟缓存添加圖片
     * 将下载的圖片写入檔案系统，實現磁盘缓存
     */
    private Bitmap loadBitmapFromHttp(String url, int reqWidth, int reqHeight)
            throws IOException {

        /*判斷目前是否為主執行緒*/
        if (Looper.myLooper() == Looper.getMainLooper()) {
            /*如果是主執行的話拋出RuntimeException*/
            throw new RuntimeException("can not visit netWork from UI Thread");
        }

        if (mDiskLruCache == null) {
            return null;
        }

        String key = hashKeyFormUrl(url);
        DiskLruCache.Editor editor = mDiskLruCache.edit(key);
        if (editor != null) {
            OutputStream outputStream = editor.newOutputStream(DISK_CACHE_INDEX);
            if (downloadUrlToStream(url, outputStream)) {
                editor.commit();
            } else {
                editor.abort();
            }
            mDiskLruCache.flush();
        }
        return loadBitmapFromDiskCache(url, reqWidth, reqHeight);
    }

    /**
     * 硬碟缓存獲得圖片
     */
    private Bitmap loadBitmapFromDiskCache(String url, int reqWidth, int reqHeight)
            throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new RuntimeException("load bitmap from UI Thread,it's not recommended");
        }

        if (mDiskLruCache == null) {
            return null;
        }
        
        Bitmap bitmap = null;
        String key = hashKeyFormUrl(url);
        DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
        if (snapshot != null) {
            FileInputStream fileInputStream = (FileInputStream) snapshot.getInputStream(DISK_CACHE_INDEX);
            FileDescriptor fileDescriptor = fileInputStream.getFD();
            bitmap = mImageResizer.decodeSampleFromFileDescriptor(fileDescriptor, reqWidth, reqHeight);

            /*把圖片資料從到內存*/
            if (bitmap != null) {
                addBitmapToMemoryCache(key, bitmap);
            }
        }
        return bitmap;
    }


    /**
     * 獲取緩存的地址
     */
    private File getDiskCacheDir(Context mContext, String uniqueName) {
        /*SD卡的狀況是否可讀寫*/
        boolean externalStorageAvailable = Environment
                .getExternalStorageState().equals(Environment.MEDIA_MOUNTED);

        String cachePath;
        if (externalStorageAvailable) {
            cachePath = mContext.getExternalCacheDir().getPath();//獲取 sdcard/Android/data/<application package>/cache
        } else {
            cachePath = mContext.getCacheDir().getPath();//獲取 /data/data/<application package>/cache
        }

        return new File(cachePath + File.separator + uniqueName);
    }

    /**
     * 得到當前可用空間大小
     *
     * @param path 檔案的路徑
     */
    private long getUsableSpace(File path) {
        return path.getUsableSpace();
    }


    /**
     * 將下載網址換成MD5的key
     * @param url 圖片的下載網址
     */
    private String hashKeyFormUrl(String url) {
        String cacheKey;
        MessageDigest mDigest;
        try {
            mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(url.getBytes());
            cacheKey = bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(url.hashCode());
        }

        return cacheKey;
    }

    /**
     * 將Url的位元組陣列轉換成hash字串
     * @param bytes URL的字节数组
     */
    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }


    /**
     * 將URL中的圖片保存到outputstream中
     *
     * @param urlString    图片的URL地址
     * @param outputStream 输出流
     * @return
     */
    private boolean downloadUrlToStream(String urlString, OutputStream outputStream) {
        HttpURLConnection urlConnection = null;
        BufferedOutputStream out = null;
        BufferedInputStream in = null;
        try {
            URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream(), IO_BUFFER_SIZE);
            out = new BufferedOutputStream(outputStream, IO_BUFFER_SIZE);

            int b;
            while ((b = in.read()) != -1) {
                out.write(b);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * load bitmap from memory cache or disk cache or network.
     * 同步加載的方法需要再子線程使用。
     * 首先嘗試從記憶體緩存中讀取圖片，接著嘗試從硬碟緩存中讀取圖片，最後才會從網路中下載。
     * @param uri       http url
     * @param reqWidth  the width ImageView desired
     * @param reqHeight the height ImageView desired
     * @return bitmap, maybe null.
     */
    public Bitmap loadBitmap(String uri, int reqWidth, int reqHeight) {
        Bitmap bitmap = loadBitmapFromMemCache(uri); //從內存獲取圖片

        if (bitmap != null) {
            Log.e(TAG, "loadBitmapFromMemCache,url " + uri);
            return bitmap;
        }

        try {
            bitmap = loadBitmapFromDiskCache(uri, reqWidth, reqHeight); //從硬碟緩存獲取圖片

            if (bitmap != null) {
                Log.e(TAG, "loadBitmapFromDiskCache,url " + uri);
                return bitmap;
            }

            bitmap = loadBitmapFromHttp(uri, reqWidth, reqHeight);
            Log.e(TAG, "loadBitmapFromHttp,url " + uri);
        } catch (IOException e) {
            e.printStackTrace();
        }


        if (bitmap == null && !mIsDiskLruCacheCreated) {
            Log.e(TAG, "encounter error,DiskLruCache is not created.");
            bitmap = downLoadBitmapFromUrl(uri);//從網路上下載圖片
        }
        return bitmap;
    }

    /**
     * 從內存獲取 bitmap
     * @param url 圖片的下載網址
     */
    private Bitmap loadBitmapFromMemCache(String url) {
        String key = hashKeyFormUrl(url);
        Bitmap bitmap = getBitmapFromMemoryCache(key);
        return bitmap;
    }

    /**
     * 從網路上下載圖片
     */
    private Bitmap downLoadBitmapFromUrl(String uri) {
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

            try {

                if (in != null) {
                    in.close();
                }
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
        return bitmap;
    }

    /**
     * 非同步加載步圖片
     * @param uri 下載網址
     * @param imageView 要顯示的imageview 元件
     * @param reqWidth 元件寬度
     * @param reqHeight 元件高度
     */
    public void bindBitmap(final String uri, final ImageView imageView, final int reqWidth, final int reqHeight) {
        imageView.setTag(TAG_KEY_URI, uri);//設定標籤

        Bitmap bitmap = loadBitmapFromMemCache(uri);

        /*如果內存有資料的話,將圖片設定到imageview*/
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
            return;
        }

        Runnable loadBitmapTask = new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = loadBitmap(uri, reqWidth, reqHeight);

                if (bitmap != null) {
                    LoaderResult result = new LoaderResult(imageView, uri, bitmap);
                    Message message = mMainHandler.obtainMessage(MESSAGE_POST_RESULT,result);
                    message.sendToTarget();
                }
            }
        };

        THREAD_POOL_EXECUTOR.execute(loadBitmapTask);//執行異步加載內Runnable()的內容
    }

    //用於構造執行緒池
    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            // TODO Auto-generated method stub
            return new Thread(r, "ImageLoader#" + mCount.getAndIncrement());
        }
    };

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

    /*建立線程池 用來執行任務的執行緒池，任務最終會交給它來處理*/
    private static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE, TimeUnit.SECONDS,
            new LinkedBlockingDeque<Runnable>(), sThreadFactory);

}
