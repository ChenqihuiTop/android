package com.cqh.android.media;

import java.net.HttpURLConnection;


public class HttpUtils {
    private static final String TAG = HttpUtils.class.getSimpleName();

    /**
     * 得到响应头中的完整文件大小
     */
    public static int getContentSize(HttpURLConnection connection) {
        int contentSize = -1;
        String contentRange = connection.getHeaderField("Content-Range");
        if (contentRange != null) {
            contentSize = Integer.valueOf(contentRange.substring(contentRange.indexOf("/") + 1));
        } else {
            String contentLength = connection.getHeaderField("Content-Length");
            if (contentLength != null) {
                contentSize = Integer.valueOf(contentLength);
            }
        }
        return contentSize;
    }

    /**
     * 得到请求头中的文件开始位置
     */
    public static int getRangeStart(HttpURLConnection connection) {
        String range = connection.getRequestProperty("Range");
        if (range != null) {
            return Integer.valueOf(range.substring(range.indexOf("bytes=") + 6, range.indexOf("-")));
        }
        return 0;
    }

}
