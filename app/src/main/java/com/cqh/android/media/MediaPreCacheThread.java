package com.cqh.android.media;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MediaPreCacheThread extends Thread {
    private static final String TAG = MediaPreCacheThread.class.getSimpleName();

    /** 预缓存文件大小 */
    public static final int PRECACHE_SIZE = 312 * 1024;

    public static void preLoad(Context context, String urlString) {
        if (MediaCacheFile.isCacheable(urlString)) {
            try {
                MediaCacheFile cacheFile = MediaCacheFile.getInstance(context, new URL(urlString));
                boolean abnormal = false; // 缓存文件是否异常不可用
                if (cacheFile == null || (abnormal = !cacheFile.isAvailable()) || cacheFile.getNeedDownloadLength(0) != -1) {
                    if (abnormal) {
                        Log.e(TAG, "缓存数据异常不可用，初始化缓存信息，然后预缓存");
                        cacheFile.initCacheParts();
                    }
                    // 如果文件的缓存不存在，或者不可用，或者文件的头部没有缓存，都需要预加载
                    new MediaPreCacheThread(context, cacheFile, getConnection(urlString)).start();
                } else {
                    Log.d(TAG, "缓存控制信息: " + cacheFile.getCacheParts() + "  " + cacheFile.getFileSize());
                    Log.d(TAG, "不需要预缓存 " + cacheFile.getFile().getName());
                }
            } catch (IOException e) {
                Log.e(TAG, "open mConnection fail 导致预缓存失败", e);
            }
        }
    }

    private static HttpURLConnection getConnection(String urlString) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        // 取消gzip数据压缩，避免内容长度不准确
        connection.setRequestProperty("Accept-Encoding", "identity");
        // 添加默认Range，方便后续处理
        connection.setRequestProperty("Range", "bytes=0-");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        return connection;
    }

    private Context mContext;
    private MediaCacheFile mCacheFile;
    private HttpURLConnection mConnection;
    private InputStream mData;

    public MediaPreCacheThread(Context context, MediaCacheFile cacheFile, HttpURLConnection connection) {
        mContext = context;
        mCacheFile = cacheFile;
        mConnection = connection;
    }

    @Override
    public void run() {
        try {
            Log.i(TAG, "================ 一个预缓存线程开启 ================= " + getId());
            mConnection.connect();
            int code = mConnection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                throw new IOException("responseCode= " + code + "  URL-> " + mConnection.getURL());
            }
            int contentSize = HttpUtils.getContentSize(mConnection);
            if (contentSize <= 0) {
                Log.e(TAG, "预缓存 网络请求的文件长度<=0");
                return;
            }
            if (mCacheFile == null) {
                mCacheFile = MediaCacheFile.getInstance(mContext, mConnection.getURL(), contentSize);
            } else if (contentSize != mCacheFile.getFileSize()) {
                Log.e(TAG, "预缓存 网络请求的文件长度和缓存控制信息中的文件长度不一致，冲突，初始化缓存文件长度和信息，然后预缓存");
                mCacheFile.initFileSize(contentSize);
            }
            mData = mConnection.getInputStream();
            byte[] buff = new byte[PRECACHE_SIZE];
            int needDownloadLength = Math.min(PRECACHE_SIZE, mCacheFile.getNeedDownloadLength(0));
            int hasDownloadLength = 0;
            final int MAX_LENGTH = 40 * 1024;
            int readBytes;
            while (needDownloadLength - hasDownloadLength > 0) {
                try {
                    readBytes = mData.read(buff, hasDownloadLength, Math.min(needDownloadLength - hasDownloadLength, MAX_LENGTH));
                } catch (IOException e) {
                    mCacheFile.insert(0, buff, hasDownloadLength);
                    Log.d(TAG, "预缓存 读取网络请求内容时出错，将缓冲区里的数据插入缓存文件，再抛出错误");
                    throw e;
                }
                if (readBytes != -1) {
                    hasDownloadLength += readBytes;
                    if (needDownloadLength - hasDownloadLength <= 0) {
                        // 下载完毕时，将buff中的数据插入缓存文件
                        if (mCacheFile.insert(0, buff, hasDownloadLength)) {
                            Log.d(TAG, mCacheFile.getFile().getName() + " 预缓存完成 0-" + hasDownloadLength);
                        } else {
                            Log.d(TAG, mCacheFile.getFile().getName() + " 预缓存写入时失败");
                        }
                        return;
                    }
                } else {
                    Log.e(TAG, "预缓存 缓存控制信息告诉我还能从网络读数据，可流的结束已到达，将缓冲区里的数据插入缓存文件，再结束");
                    mCacheFile.insert(0, buff, hasDownloadLength);
                    return;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "预缓存 发生异常");
            e.printStackTrace();
        } finally {
            if (mData != null) {
                try {
                    mData.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            mConnection.disconnect();
            Log.i(TAG, "================ 一个预缓存线程关闭 ================= " + getId());
        }
    }

}

