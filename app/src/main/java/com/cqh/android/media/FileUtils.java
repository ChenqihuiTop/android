package com.cqh.android.media;

import android.util.Log;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class FileUtils {
	private static final String TAG = FileUtils.class.getSimpleName();


	/**
	 * 判断存储是否可用
	 *
	 * @return
	 */
	public static boolean isSdAvaliable(String dirPath, int minRemainSize) {
		// 判断外部存储器是否可用
		File dir = new File(dirPath);
		dir.mkdirs();
		if(!dir.exists()){
			return false;
		}
		// 可用空间大小是否大于SD卡预留最小值
		long freeSize = dir.getUsableSpace();
		Log.d(TAG, "缓存目录可用空间大小 " + freeSize);
		if (freeSize > minRemainSize) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * 获取文件夹内的文件，按日期排序，从旧到新
	 *
	 * @param dirPath
	 * @return
	 */
	public static ArrayList<File> getFilesSortByDate(String dirPath) {
		File dir = new File(dirPath);
		File[] files = dir.listFiles();
		if (files == null || files.length == 0)
			return null;
		Arrays.sort(files, new Comparator<File>() {
			public int compare(File f1, File f2) {
				return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
			}
		});
		ArrayList<File> result = new ArrayList<File>();
		for(int i=0; i<files.length; i++) {
			if (!files[i].isDirectory()) {
				result.add(files[i]);
			}
		}
		return result;
	}

	/**
	 * 获取有效的文件名
	 */
	public static String getValidFileName(URL url) {
		String path = url.getPath();
		String name = path.substring(path.lastIndexOf("/"));
		name = name.replace("\\", "");
		name = name.replace("/", "");
		name = name.replace(":", "");
		name = name.replace("*", "");
		name = name.replace("?", "");
		name = name.replace("\"", "");
		name = name.replace("<", "");
		name = name.replace(">", "");
		name = name.replace("|", "");
		name = name.replace(" ", "_"); // 前面的替换会产生空格,最后将其一并替换掉
		return name;
	}

	/**
	 * 获取有效的文件名
	 */
	public static String getValidFileName(String urlString) {
		URL url;
		try {
			url = new URL(urlString);
		} catch (MalformedURLException e) {
			return null;
		}
		String path = url.getPath();
		String name = path.substring(path.lastIndexOf("/"));
		name = name.replace("\\", "");
		name = name.replace("/", "");
		name = name.replace(":", "");
		name = name.replace("*", "");
		name = name.replace("?", "");
		name = name.replace("\"", "");
		name = name.replace("<", "");
		name = name.replace(">", "");
		name = name.replace("|", "");
		name = name.replace(" ", "_"); // 前面的替换会产生空格,最后将其一并替换掉
		return name;
	}


}
