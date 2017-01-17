package com.cqh.android.media;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.util.Log;

import java.io.File;


public class MediaCacheFileInfoDB extends SQLiteOpenHelper {
	private static final String TAG = MediaCacheFileInfoDB.class.getSimpleName();

	static final int DB_VERSION = 1;
	static final String DB_PATH = Environment.getExternalStorageDirectory().getPath() + "/" + "cqh/Cache/DB/";
	static final String DB_NAME = "CacheFileInfo.db";
	static final String TABLE_NAME = "mediaCacheFileInfo";
	static final String FIELD_FILE_NAME = "fileName";
	static final String FIELD_FILE_SIZE = "fileSize";
	static final String FIELD_CACHE_PARTS = "cacheParts";
	static final String FIELD_DURATION = "duration";

	private static MediaCacheFileInfoDB mDB;
	private static File mDBFile = new File(DB_PATH + DB_NAME);

	private MediaCacheFileInfoDB(Context context) {
		super(context, DB_PATH + DB_NAME, null, DB_VERSION);
	}

	synchronized public static MediaCacheFileInfoDB getInstance(Context context) {
		if (mDB == null || !mDBFile.exists()) {
			if (mDB != null) {
				mDB.close();
			}
			new File(DB_PATH).mkdirs();
			mDB = new MediaCacheFileInfoDB(context.getApplicationContext());
		}
		return mDB;
	}

	// 当数据库首次创建时执行该方法，一般将创建表等初始化操作放在该方法中执行.
	@Override
	public void onCreate(SQLiteDatabase db) {
		Log.i(TAG, "CreateTable " + TABLE_NAME);
		db.execSQL("CREATE TABLE " + TABLE_NAME + "("
				+ FIELD_FILE_NAME +" STRING PRIMARY KEY,"
				+ FIELD_FILE_SIZE + " INTEGER,"
				+ FIELD_CACHE_PARTS + " STRING,"
				+ FIELD_DURATION + " INTEGER)");
	}

	// 当打开数据库时传入的版本号比当前的版本号高时会调用该方法
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.d(TAG, "数据库" + DB_NAME + "从版本" + oldVersion + "升级到版本" + newVersion);
		// TODO db.execSQL（change）
	}

	// 当打开数据库时传入的版本号比当前的版本号低时会调用该方法
	@Override
	public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.d(TAG, "数据库" + DB_NAME + "从版本" + oldVersion + "降级到版本" + newVersion);
		// TODO db.execSQL（change）
	}

	public static boolean isExist(Context context, String fileName) {
		Cursor cursor = getInstance(context).getReadableDatabase().rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE " + FIELD_FILE_NAME + "=?", new String[]{fileName});
		boolean isExist = false;
		if (cursor != null) {
			if (cursor.moveToFirst()) {
				isExist = true;
			}
			cursor.close();
		}
		return isExist;
	}

	public static void insertOrUpdate(Context context, String fileName, int fileSize) {
		if (isExist(context, fileName)) {
			updateFileSize(context, fileName, fileSize);
		} else {
			insert(context, fileName, fileSize);
		}
	}

	public static void insert(Context context, String fileName, int fileSize) {
		SQLiteDatabase sqLiteDatabase = getInstance(context).getWritableDatabase();
		sqLiteDatabase.beginTransaction();
		try {
			ContentValues cv = new ContentValues();
			cv.put(FIELD_FILE_NAME, fileName);
			cv.put(FIELD_FILE_SIZE, fileSize);
			sqLiteDatabase.insert(TABLE_NAME, null, cv);
			sqLiteDatabase.setTransactionSuccessful();
		} finally {
			sqLiteDatabase.endTransaction();
		}
	}

	public static void updateFileSize(Context context, String fileName, int fileSize) {
		SQLiteDatabase sqLiteDatabase = getInstance(context).getWritableDatabase();
		sqLiteDatabase.beginTransaction();
		try {
			ContentValues cv = new ContentValues();
			cv.put(FIELD_FILE_SIZE, fileSize);
			sqLiteDatabase.update(TABLE_NAME, cv, FIELD_FILE_NAME + "=?", new String[] { fileName });
			sqLiteDatabase.setTransactionSuccessful();
		} finally {
			sqLiteDatabase.endTransaction();
		}
	}

	public static void updateCacheParts(Context context, String fileName, String cacheParts) {
		SQLiteDatabase sqLiteDatabase = getInstance(context).getWritableDatabase();
		sqLiteDatabase.beginTransaction();
		try {
			ContentValues cv = new ContentValues();
			cv.put(FIELD_CACHE_PARTS, cacheParts);
			sqLiteDatabase.update(TABLE_NAME, cv, FIELD_FILE_NAME + "=?", new String[] { fileName });
			sqLiteDatabase.setTransactionSuccessful();
		} finally {
			sqLiteDatabase.endTransaction();
		}
	}

	public static void delete(Context context, String fileName) {
		SQLiteDatabase sqLiteDatabase = getInstance(context).getWritableDatabase();
		sqLiteDatabase.beginTransaction();
		try {
			sqLiteDatabase.delete(TABLE_NAME, FIELD_FILE_NAME + "=?", new String[] { fileName });
			sqLiteDatabase.setTransactionSuccessful();
		} finally {
			sqLiteDatabase.endTransaction();
		}
	}

	public static MediaCacheFileInfo getCacheFileInfo(Context context, String fileName) {
		Cursor cursor = getInstance(context).getReadableDatabase().rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE " + FIELD_FILE_NAME + "=?", new String[] { fileName });
		MediaCacheFileInfo cacheFileInfo = null;
		if (cursor != null) {
			if (cursor.moveToFirst()) {
				int fileSize = cursor.getInt(cursor.getColumnIndex(FIELD_FILE_SIZE));
				String cacheParts = cursor.getString(cursor.getColumnIndex(FIELD_CACHE_PARTS));
				int duration = cursor.getInt(cursor.getColumnIndex(FIELD_DURATION));
				cacheFileInfo = new MediaCacheFileInfo(fileName, fileSize, cacheParts, duration);
			}
			cursor.close();
		}
		return cacheFileInfo;
	}

	public static void updateCacheFileInfo(Context context, String fileName, MediaCacheFileInfo cacheFileInfo) {
		SQLiteDatabase sqLiteDatabase = getInstance(context).getWritableDatabase();
		sqLiteDatabase.beginTransaction();
		try {
			ContentValues cv = new ContentValues();
			cv.put(FIELD_CACHE_PARTS, cacheFileInfo.cacheParts);
			cv.put(FIELD_FILE_SIZE, cacheFileInfo.fileSize);
			sqLiteDatabase.update(TABLE_NAME, cv, FIELD_FILE_NAME + "=?", new String[] { fileName });
			sqLiteDatabase.setTransactionSuccessful();
		} finally {
			sqLiteDatabase.endTransaction();
		}
	}

	public static class MediaCacheFileInfo {
		public String fileName;
		public int fileSize;
		public String cacheParts;
		public int duration;

		public MediaCacheFileInfo(String fileName, int fileSize, String cacheParts, int duration) {
			this.fileName = fileName;
			this.fileSize = fileSize;
			this.cacheParts = cacheParts;
			this.duration = duration;
		}
	}
}
