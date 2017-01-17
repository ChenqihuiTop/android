package com.cqh.android;

import android.app.Application;


public class App extends Application {
	public static final String TAG = "APP";

	public static App mApp;  // Application单例

	@Override
	public void onCreate() {
		super.onCreate();
		// 初始化application单例
		mApp = this;
	}
}
