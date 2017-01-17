package com.cqh.android.util;

import android.content.Context;
import android.view.Gravity;
import android.widget.Toast;

import com.cqh.android.App;

import java.io.UnsupportedEncodingException;

/**
 * Created by Top on 2017-01-04.
 */

public class Utils {
    private static final String TAG = Utils.class.getSimpleName();
    private static Context mApp = App.mApp;

    private static Toast centerToast, bottomToast;


    /**
     * 获得字符串资源
     */
    public static String getString(int resId){
        return mApp.getString(resId);
    }

    /**
     * 获得带参数的字符串资源
     */
    public static String getString(int resId, Object... args){
        return String.format(mApp.getString(resId), args);
    }


    /**
     * 消息提示，显示在屏幕中央
     */
    public static void showCenterToast(int msgId) {
        showToast(getString(msgId), Gravity.CENTER);
    }

    /**
     * 消息提示，显示在屏幕中央
     */
    public static void showCenterToast(int msgId, Object... args) {
        showToast(getString(msgId, args), Gravity.CENTER);
    }

    /**
     * 消息提示，显示在屏幕底部
     */
    public static void showBottomToast(int msgId) {
        showToast(getString(msgId), Gravity.BOTTOM);
    }

    /**
     * 消息提示，显示在屏幕底部
     */
    public static void showBottomToast(int msgId, Object... args) {
        showToast(getString(msgId, args), Gravity.BOTTOM);
    }

    /**
     * 消息提示
     */
    public static void showToast(String msg, int gravity) {
        Toast newToast, oldToast = null;
        newToast = Toast.makeText(mApp, msg, Toast.LENGTH_SHORT);
        switch (gravity) {
            case Gravity.CENTER:
                newToast.setGravity(gravity, 0, 0);
                oldToast = centerToast;
                centerToast = newToast;
                break;
            case Gravity.BOTTOM:
                newToast.setGravity(gravity, 0, 30);
                oldToast = bottomToast;
                bottomToast = newToast;
                break;
        }
        if (oldToast != null) {
            oldToast.cancel();
        }
        newToast.show();
    }

    /**
     * 将毫秒转化为分秒的字符串，四舍五入
     */
    public static String parseMilliseconds(long milliseconds) {
        milliseconds = milliseconds + 500;
        int second = (int) (milliseconds / 1000);
        int m = second / 60;
        int s = second % 60;
        String mm = String.valueOf(m), ss = String.valueOf(s);
        return (m < 10 ? "0"+mm : mm) + ":" + (s < 10 ? "0"+ss : ss);
    }

    /**
     * URL编码
     */
    public static String urlEncode(String url) {
        try {
            url = java.net.URLEncoder.encode(url, "UTF-8");
            url = url.replaceAll("%2F", "/");
            url = url.replaceAll("%3A", ":");
            url = url.replaceAll("\\+", "%20");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return url;
    }

}
