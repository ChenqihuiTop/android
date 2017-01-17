package com.cqh.android.media;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.OverlappingFileLockException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;


public class MediaCacheFile {
	private static final String TAG = MediaCacheFile.class.getSimpleName();

	private Context mContext;
	private File mFile;

	// 文件名一一对应锁，确保同时只有一个线程对同一个文件进行读写
	private static HashMap<String, Object> mFileLocks = new HashMap<String, Object>();

	/** SD卡预留最小值 */
	public static final int DIR_MIN_REMAIN_SIZE = 50 * 1024 * 1024;
	/** 缓存保存路径 */
	public static final String CACHE_FILE_PATH = Environment.getExternalStorageDirectory().getPath() + "/" + "cqh/Cache/Media/";
	/** 缓存文件后缀 */
	public static final String CACHE_FILE_SUFFIX = ".cache";


	public static boolean isCacheable(String urlString) {
		try {
			URL url = new URL(urlString);
			return isCacheable(url);
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return false;
		}
	}

	public static boolean isCacheable(URL url) {
		if (!FileUtils.isSdAvaliable(CACHE_FILE_PATH, DIR_MIN_REMAIN_SIZE)) {
			Log.e(TAG, "缓存目录不可用或可用空间不足");
			return false;
		}
		String name = FileUtils.getValidFileName(url);
		if (name == null || name.length() <= 0) {
			Log.e(TAG, "无法从url中获取文件名");
			return false;
		}
		return true;
	}

	private MediaCacheFile(Context context, String name) {
		mContext = context;
		new File(CACHE_FILE_PATH).mkdirs();
		mFile = new File(CACHE_FILE_PATH + name);
		if (!mFile.exists()) {
			try {
				mFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 在数据库中必须有该文件的缓存控制信息，才能返回cacheFile
	 */
	public static MediaCacheFile getInstance(Context context, URL url) {
		String name = FileUtils.getValidFileName(url) + CACHE_FILE_SUFFIX;
		if (!MediaCacheFileInfoDB.isExist(context, name)) {
			return null;
		}
		MediaCacheFile cacheFile = new MediaCacheFile(context, name);
		return cacheFile;
	}

	/**
	 * 在数据库中插入或更新该文件的缓存控制信息，并返回cacheFile
	 */
	public static MediaCacheFile getInstance(Context context, URL url, int fileSize) {
		String name = FileUtils.getValidFileName(url) + CACHE_FILE_SUFFIX;
		MediaCacheFileInfoDB.insertOrUpdate(context, name, fileSize);
		MediaCacheFile cacheFile = new MediaCacheFile(context, name);
		return cacheFile;
	}


	public File getFile() {
		return mFile;
	}

	public int getFileSize() {
		MediaCacheFileInfoDB.MediaCacheFileInfo info = getCacheFileInfo();
		if (info != null) {
			return info.fileSize;
		} else {
			return -1;
		}
	}

	public String getCacheParts() {
		MediaCacheFileInfoDB.MediaCacheFileInfo info = getCacheFileInfo();
		if (info != null) {
			return info.cacheParts;
		} else {
			return null;
		}
	}

	public MediaCacheFileInfoDB.MediaCacheFileInfo getCacheFileInfo() {
		return MediaCacheFileInfoDB.getCacheFileInfo(mContext, mFile.getName());
	}



	private synchronized static Object getFileLock(String fileName) {
		Object fileLock = mFileLocks.get(fileName);;
		if (fileLock == null) {
			fileLock = new Object();
			mFileLocks.put(fileName, fileLock);
		}
		return fileLock;
	}

	/**
	 * 初始化文件长度，说明原来的缓存数据已经不可用，需先初始化缓存信息
	 */
	public void initFileSize(int fileSize) {
		initCacheParts();
		MediaCacheFileInfoDB.insertOrUpdate(mContext, mFile.getName(), fileSize);
	}

	/**
	 * 如果缓存文件长度与缓存控制信息一致，则缓存数据可用
	 *
	 * @return true if cacheLengthByList == cacheLengthByFile, else false
	 */
	public boolean isAvailable() {
		return getCacheLengthByList(parseCacheParts(getCacheParts())) == mFile.length();
	}

	public void initCacheParts() {
		synchronized (getFileLock(mFile.getName())) {
			RandomAccessFile raf = null;
			try {
				raf = new RandomAccessFile(mFile, "rw");
				try {
					raf.getChannel().lock();
				} catch (OverlappingFileLockException e) {
					// 锁上加锁可能导致该异常，不做处理
				}
				raf.setLength(0);
				MediaCacheFileInfoDB.updateCacheParts(mContext, mFile.getName(), null);
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (raf != null){
					try {
						raf.close();
					} catch (IOException e) {
						Log.e(TAG, "raf.close() error 问号脸", e);
					}
				}
			}
		}
	}

	public void delete() {
		if (mFile.delete()) {
			MediaCacheFileInfoDB.delete(mContext, mFile.getName());
		}
	}




	/**
	 * @param start 写入哪个位置开始的缓存数据
	 * @param data 要写入的数据
	 *
	 * @return 如果插入成功，返回true，如果取消插入，返回false
	 */
	public boolean insert(int start, byte[] data){
		return insert(start, data, data.length);
	}

	/**
	 * @param start 插入哪个位置开始的缓存数据
	 * @param data 要插入的数据，从0开始
	 * @param length 要插入的数据长度
	 *
	 * @return 如果插入成功，返回true，如果取消插入，返回false
	 */
	public boolean insert(int start, byte[] data, int length){
		synchronized (getFileLock(mFile.getName())) {
			if (start < 0 || length <= 0)
				return false;
			RandomAccessFile raf = null;
			try {
				raf = new RandomAccessFile(mFile, "rw");
				raf.getChannel().lock();
				ArrayList<CachePart> cachePartList = parseCacheParts(getCacheParts());
				int skip = updateCachePartList(cachePartList, start, start + length - 1);
				if (skip == -1) {
					// 可能是和预缓存后的状态冲突了，这里只处理这种情况，其他意料之外的情况作异常处理
					if (start == 0 && cachePartList != null && cachePartList.size() > 0
							&& cachePartList.get(0).start == 0
							&& cachePartList.get(0).end < start + length - 1) {
						start = cachePartList.get(0).end + 1;
						data = Arrays.copyOfRange(data, start, length);
						return insert(start, data, length - start);
					}
					Log.e(TAG, "INSERT 和控制信息匹配有问题，取消插入");
					return false;
				} else if (skip == -2) {
					Log.e(TAG, "INSERT 缓存数据异常，初始化后重新插入");
					initCacheParts();
					return insert(start, data, length);
				}
				int totalMoveLength = (int) raf.length() - skip;
				int alreadyMoveLength = 0;
				final int TEMP_LENGTH = Math.min(totalMoveLength, 256 * 1024);
				byte[] temp = new byte[TEMP_LENGTH];
				raf.setLength(raf.length() + length);
				while (totalMoveLength - alreadyMoveLength > 0) {
					int tempLength = Math.min(totalMoveLength - alreadyMoveLength, TEMP_LENGTH);
					raf.seek(raf.length() - length - alreadyMoveLength - tempLength);
					raf.readFully(temp, 0, tempLength);
					raf.seek(raf.length() - alreadyMoveLength - tempLength);
					raf.write(temp, 0, tempLength);
					alreadyMoveLength += tempLength;
				}
				raf.seek(skip);
				raf.write(data, 0, length);
				MediaCacheFileInfoDB.updateCacheParts(mContext, mFile.getName(), parseCachePartList(cachePartList));
				Log.d(TAG, "√√√↓↓↓-- INSERT 缓存 length:" + length + "  " + start + "-" + (start + length - 1) + " --↓↓↓√√√");
				Log.d(TAG, "缓存控制信息: " + getCacheParts() + "  " + getFileSize());
				return true;
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			} finally {
				if (raf != null){
					try {
						raf.close();
					} catch (IOException e) {
						Log.e(TAG, "raf.close() error 问号脸", e);
					}
				}
			}
		}
	}

	private static ArrayList<CachePart> parseCacheParts(String cacheParts) {
		ArrayList<CachePart> cachePartList = new ArrayList<CachePart>();
		if (!TextUtils.isEmpty(cacheParts)) {
			String[] strs = cacheParts.split(",");
			for (String str : strs) {
				String[] ss = str.split("-");
				cachePartList.add(new CachePart(Integer.valueOf(ss[0]), Integer.valueOf(ss[1])));
			}
		}
		return cachePartList;
	}

	/**
	 * 将list更新为假设给缓存文件插入range为start-end的数据后的cachePartList
	 * @param list 要更新的list
	 * @param start 要插入的缓存数据的起始位置
	 * @param end 要插入的缓存数据的结束位置
	 *
	 * @return 若缓存文件长度和缓存控制信息本身对不上，则需要初始化缓存信息，返回-2；
	 * 若只是start-end与缓存控制信息冲突，则可能是预缓存的原因，需进一步处理，返回-1；
	 * 若无任何问题，则返回在缓存文件中开始插入的位置
	 */
	private int updateCachePartList(ArrayList<CachePart> list, int start, int end) {
		int cacheLengthByList = getCacheLengthByList(list);
		if (cacheLengthByList != mFile.length()) {
			Log.e(TAG, "缓存文件长度与缓存控制信息不一致");
			return -2;
		}
		if (list.size() == 0) {
			list.add(new CachePart(start, end));
			return 0;
		}
		if (start >= 0 && end < list.get(0).start) {
			if (end == list.get(0).start - 1) {
				list.get(0).start = start;
			} else {
				list.add(0, new CachePart(start, end));
			}
			return 0;
		}
		int cacheLength = 0;
		for (int i=0; i<list.size()-1; i++) {
			cacheLength += (list.get(i).end - list.get(i).start + 1);
			if (start > list.get(i).end && end < list.get(i+1).start) {
				if (start == list.get(i).end + 1 && end == list.get(i+1).start - 1) {
					list.get(i).end = list.get(i+1).end;
					list.remove(i+1);
				} else if (start == list.get(i).end + 1) {
					list.get(i).end = end;
				} else if (end == list.get(i+1).start - 1) {
					list.get(i+1).start = start;
				} else {
					list.add(i + 1, new CachePart(start, end));
				}
				return cacheLength;
			}
		}
		if (start > list.get(list.size() - 1).end && end < getFileSize()) {
			if (start == list.get(list.size() - 1).end + 1) {
				list.get(list.size() - 1).end = end;
			} else {
				list.add(list.size(), new CachePart(start, end));
			}
			return cacheLengthByList;
		}
		Log.e(TAG, "待插入的数据信息与缓存控制信息不匹配");
		return -1;
	}

	private static int getCacheLengthByList(final ArrayList<CachePart> list) {
		int cacheLength = 0;
		if (list != null) {
			for (CachePart part : list) {
				cacheLength += (part.end - part.start + 1);
			}
		}
		return cacheLength;
	}

	private static String parseCachePartList(ArrayList<CachePart> cachePartList) {
		String cacheParts = "";
		for (CachePart cachePart : cachePartList) {
			cacheParts += (cachePart.start + "-" + cachePart.end + ",");
		}
		if (!TextUtils.isEmpty(cacheParts)) {
			cacheParts = cacheParts.substring(0, cacheParts.length() - 1);
		}
		return cacheParts;
	}







	/**
	 * @param buffer 存放读取的数据的数组
	 * @param start 读取哪个位置开始的缓存数据
	 *
	 * @return 如果缓存数据异常取消读取并初始化，返回-1，否则返回读取的长度
	 */
	public int read(byte[] buffer, int start) {
		synchronized (getFileLock(mFile.getName())) {
			RandomAccessFile raf = null;
			try {
				raf = new RandomAccessFile(mFile, "rw");
				raf.getChannel().lock();
				int[] skipAndLength = getReadSkipAndLength(start, buffer.length);
				if (skipAndLength == null) {
					Log.e(TAG, "READ 缓存文件长度与缓存控制信息不一致，取消读取并初始化");
					initCacheParts();
					return -1;
				} else if (skipAndLength.length != 2) {
					Log.e(TAG, "READ 待读取的数据信息与缓存控制信息不匹配，取消读取");
					return 0;
				}
				int skip = skipAndLength[0];
				int length = skipAndLength[1];
				raf.seek(skip);
				raf.readFully(buffer, 0, length);
				Log.d(TAG, "√√√↑↑↑-- READ 缓存 length:" + length + "  " + start + "-" + (start + length - 1) + " --↑↑↑√√√");
				return length;
			} catch (IOException e) {
				e.printStackTrace();
				return 0;
			} finally {
				if (raf != null){
					try {
						raf.close();
					} catch (IOException e) {
						Log.e(TAG, "raf.close() error 问号脸", e);
					}
				}
			}
		}
	}

	/**
	 * @param start 要读取的缓存数据的起始位置
	 * @param maxLength 要读取的最大长度
	 *
	 * @return 若缓存文件长度和缓存控制信息本身对不上，则需要初始化缓存信息，返回null；
	 * 若只是start、maxLength与缓存控制信息冲突，这属于未知的异常情况，一般取消读取即可，返回长度为0的int数组；
	 * 若无任何问题，则返回存放了 在缓存文件中读取的开始位置和长度 的int数组
	 */
	private int[] getReadSkipAndLength(int start, int maxLength) {
		ArrayList<CachePart> list = parseCacheParts(getCacheParts());
		if (getCacheLengthByList(list) != mFile.length()) {
			return null;
		}
		int cacheLength = 0;
		int readSkip;
		int readLength;
		for (CachePart part : list) {
			if (start >= part.start && start <= part.end) {
				readSkip = cacheLength + start - part.start;
				if (start + maxLength - 1 <= part.end) {
					readLength = maxLength;
				} else {
					readLength = part.end - start + 1;
				}
				return new int[]{readSkip, readLength};
			}
			cacheLength += (part.end - part.start + 1);
		}
		return new int[0];
	}

	static class CachePart {
		int start;
		int end;

		public CachePart(int start, int end) {
			this.start = start;
			this.end = end;
		}
	}




	/**
	 * 获得该位置接下来需要从网络下载的数据长度
	 *
	 * @param start 当前位置
	 *
	 * @return 如果当前位置可以读取缓存，返回-1，否则返回需从网络下载的数据长度
	 */
	public int getNeedDownloadLength(int start) {
		ArrayList<CachePart> list = parseCacheParts(getCacheParts());
		for (CachePart part : list) {
			if (start < part.start) {
				return part.start - start;
			} else if (start <= part.end) {
				return -1;
			}
		}
		return getFileSize() - start;
	}

	/**
	 * 获得该播放进度下的缓存进度
	 *
	 * @param playProgress 播放进度
	 *
	 * @return 当前播放进度可以读取缓存的进度
	 */
	public static float getBufferingProgress(Context context, String fileName, float playProgress) {
		if (!TextUtils.isEmpty(fileName)) {
			MediaCacheFileInfoDB.MediaCacheFileInfo cacheFileInfo = MediaCacheFileInfoDB.getCacheFileInfo(context, fileName + CACHE_FILE_SUFFIX);
			if (cacheFileInfo != null) {
				int start = (int) (playProgress * cacheFileInfo.fileSize);
				ArrayList<CachePart> list = parseCacheParts(cacheFileInfo.cacheParts);
				for (CachePart part : list) {
					if (start >= part.start && start <= part.end) {
						return (float) (part.end + 1) / cacheFileInfo.fileSize;
					}
				}
			}
		}
		return 0;
	}

	public static void getMetadata(String path){
		MediaMetadataRetriever mmr = new MediaMetadataRetriever();
		Log.d(TAG, "str:" + path);
		try {
			mmr.setDataSource(path);

			String album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
			Log.d(TAG, "album:" + album);
			Log.d(TAG, "METADATA_KEY_ALBUMARTIST: " + mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST));
			Log.d(TAG, "artist: " + mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
			Log.d(TAG, "METADATA_KEY_AUTHOR: " + mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR));
			Log.d(TAG, "bitrate: " + mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));// 从api level 14才有，即从ICS4.0才有此功能
			Log.d(TAG, "METADATA_KEY_CD_TRACK_NUMBER: " + mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER));
			Log.d(TAG, "METADATA_KEY_COMPILATION: " + mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPILATION));
			Log.d(TAG, "METADATA_KEY_COMPOSER: " + mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER));
			Log.d(TAG, "METADATA_KEY_DATE: " + mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE));
			Log.d(TAG, "METADATA_KEY_DISC_NUMBER: " + mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER));
			Log.d(TAG, "METADATA_KEY_DURATION: " + mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
			Log.d(TAG, "METADATA_KEY_GENRE: " + mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE));
			Log.d(TAG, "METADATA_KEY_HAS_AUDIO: " + mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));
			Log.d(TAG, "METADATA_KEY_HAS_VIDEO: " + mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO));
			Log.d(TAG, "METADATA_KEY_LOCATION: " + mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION));
			Log.d(TAG, "METADATA_KEY_MIMETYPE: " + mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE));

			Log.d(TAG, "METADATA_KEY_NUM_TRACKS: " + mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS));
			Log.d(TAG, "METADATA_KEY_TITLE: " + mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
			Log.d(TAG, "METADATA_KEY_VIDEO_HEIGHT: " + mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
			Log.d(TAG, "METADATA_KEY_VIDEO_ROTATION: " + mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
			Log.d(TAG, "METADATA_KEY_VIDEO_WIDTH: " + mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
			Log.d(TAG, "METADATA_KEY_WRITER: " + mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER));
			Log.d(TAG, "METADATA_KEY_YEAR: " + mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR));
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			mmr.release();
		}
	}
}
