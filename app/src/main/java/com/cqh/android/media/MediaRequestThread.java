package com.cqh.android.media;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class MediaRequestThread extends Thread {
	private static final String TAG = MediaRequestThread.class.getSimpleName();

	private Context mContext;
	private boolean mRunnable;

	private Socket mClient;

	private boolean mCacheable;
	private MediaCacheFile mCacheFile;
	private HttpURLConnection mConnection;
	private InputStream mData;
	private int mRangeStart, mDataPos;

	private RequestListener mRequestListener;
	private RequestErrorListener mRequestErrorListener;

	/** 读写缓冲区最小长度 缓冲区太小会导致数据频繁写入缓存文件，降低性能 */
	public static final int RW_BUFF_MIN_LENGTH = 256 * 1024;
	/** 读写缓冲区最大长度 缓冲区太大会占用大量内存 */
	public static final int RW_BUFF_MAX_LENGTH = 4 * 1024 * 1024;


	public MediaRequestThread(Context context, Socket client, HttpURLConnection connection, boolean cacheable, RequestListener requestListener, RequestErrorListener requestErrorListener) {
		mContext = context;
		mRunnable = true;
		mClient = client;
		mConnection = connection;
		mCacheable = cacheable;
		mRequestListener = requestListener;
		mRequestErrorListener = requestErrorListener;
	}

	public void setRunnable(boolean runnable) {
		this.mRunnable = runnable;
	}

	public void setRequestListener(RequestListener listener) {
		mRequestListener = listener;
	}

	public void setRequestErrorListener(RequestErrorListener listener) {
		mRequestErrorListener = listener;
	}

	@Override
	public void run() {
		mRangeStart = HttpUtils.getRangeStart(mConnection);
		mDataPos = mRangeStart;
		try {
            mCacheable = mCacheable && MediaCacheFile.isCacheable(mConnection.getURL()) && initCacheFile();
			if (mCacheable) {
				Log.i(TAG, "--------------------- 开始处理播放器请求，可以缓存 " + getId());
				processRequestWithCache();
			} else {
				Log.i(TAG, "------------------- 开始处理播放器请求，不可以缓存 " + getId());
				processRequestWithoutCache();
			}
		} catch (ConnectException e) {
			Log.e(TAG, "请求连接失败，应该是无可用网络 ------ " + getId());
			if (mRequestErrorListener != null) {
				mRequestErrorListener.onNetworkError();
			}
		} catch (UnknownHostException e) {
			Log.e(TAG, "无法解析域名，应该是无可用网络 ------ " + getId());
			if (mRequestErrorListener != null) {
				mRequestErrorListener.onNetworkError();
			}
		} catch (SocketTimeoutException e) {
			Log.e(TAG, "请求连接超时，应该是网络状况不佳或IP地址错误 ------ " + getId());
            if (mRequestErrorListener != null) {
				mRequestErrorListener.onTimeoutError();
			}
		} catch (SocketException e) {
			Log.i(TAG, "可能是播放器切换源或者seek了，连接被终止 ----------- " + getId());
		} catch (IOException e) {
			if (e.getMessage().startsWith("responseCode=")) {
				Log.e(TAG, "请求状态码" + e.getMessage().substring(13, 16) + "异常，应该是链接失效 ------ " + getId());
				if (mRequestErrorListener != null) {
					mRequestErrorListener.onUrlInvalidError();
				}
			} else {
				Log.e(TAG, "读写过程中发生意外错误 ---------------------------- " + getId());
				e.printStackTrace();
			}
		} finally {
			if (mData != null) {
				try {
					mData.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			mConnection.disconnect();
			try {
				mClient.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			Log.i(TAG, "============ MediaClientProxy捕获的一个播放器请求处理结束并关闭 ==================== " + getId());
		}
	}

	private boolean initCacheFile() throws IOException {
		// 缓存文件已存在的话直接先读缓存数据
        mCacheFile = MediaCacheFile.getInstance(mContext, mConnection.getURL());
        if (mCacheFile == null) {
			// 缓存文件不存在的话，需要发起网络请求，并获取文件长度来初始化缓存文件
			// 如果该请求异常，就无法从网络读取，而缓存也没用，那么没有继续处理的必要，让它抛出异常
            httpConnect();
            int contentSize = HttpUtils.getContentSize(mConnection);
            if (contentSize > 0) {
                mCacheFile = MediaCacheFile.getInstance(mContext, mConnection.getURL(), contentSize);
            }
        }
		return mCacheFile != null;
	}

	private void processRequestWithCache() throws IOException {
		int fileSize = mCacheFile.getFileSize();
		// 返回请求的响应头
		sendResponseHeader(mRangeStart, fileSize - 1, fileSize);
		final int BUFF_LENGTH = Math.min(Math.max((int) (fileSize / 9.9), RW_BUFF_MIN_LENGTH), RW_BUFF_MAX_LENGTH);
		byte[] buff = new byte[BUFF_LENGTH];
		Log.d(TAG, "缓存控制信息: " + mCacheFile.getCacheParts() + "  " + mCacheFile.getFileSize());
		while (mRunnable && mRangeStart < fileSize) {
			int needDownloadLength = mCacheFile.getNeedDownloadLength(mRangeStart);
			if (needDownloadLength == -1) {
				int readBytes = mCacheFile.read(buff, mRangeStart);
				if (readBytes > 0) {
					mClient.getOutputStream().write(buff, 0, readBytes);
					mRangeStart += readBytes;
					if (mRequestListener != null) {
						mRequestListener.onWriteIntoClient((float) mRangeStart / fileSize);
					}
				}
			} else {
				httpConnect();
				int contentSize = HttpUtils.getContentSize(mConnection);
				if (contentSize != fileSize) {
                    Log.e(TAG, "网络请求的文件长度和缓存控制信息中的文件长度不一致，冲突，初始化缓存文件长度和信息，再结束");
                    mCacheFile.initFileSize(contentSize);
					return;
				}
				final int MAX_LENGTH = 40 * 1024;
				int hasDownloadLength = 0;
				int readBytes;
				while (needDownloadLength - hasDownloadLength > 0) {
					if (!mRunnable) {
						Log.d(TAG, "读取网络请求内容时线程即将要关闭，将缓冲区里的数据插入缓存文件，再跳出循环，等待结束");
						mCacheFile.insert(mRangeStart - hasDownloadLength, buff, hasDownloadLength);
						break;
					}
					try {
						readBytes = mData.read(buff, hasDownloadLength, Math.min(needDownloadLength - hasDownloadLength, MAX_LENGTH));
					} catch (IOException e) {
						Log.d(TAG, "读取网络请求内容时出错，将缓冲区里的数据插入缓存文件，再抛出错误");
						mCacheFile.insert(mRangeStart - hasDownloadLength, buff, hasDownloadLength);
						throw e;
					}
					if (readBytes != -1) {
						mDataPos += readBytes;
						// 返回请求的数据
						mClient.getOutputStream().write(buff, hasDownloadLength, readBytes);
						mRangeStart += readBytes;
						if (mRequestListener != null) {
							mRequestListener.onWriteIntoClient((float) mRangeStart / fileSize);
						}
						hasDownloadLength += readBytes;
						if (hasDownloadLength / 1024 % 100 == 0) {
							Log.d(TAG, "√√√↑↑↑-- READ 网络 length:" + hasDownloadLength + "  " + (mRangeStart - hasDownloadLength) + "-" + (mRangeStart - 1) + " --↑↑↑√√√");
						}
						if (hasDownloadLength + MAX_LENGTH > buff.length || needDownloadLength - hasDownloadLength <= 0) {
							Log.d(TAG, "√√√↑↑↑-- READ 网络 length:" + hasDownloadLength + "  " + (mRangeStart - hasDownloadLength) + "-" + (mRangeStart - 1) + " --↑↑↑√√√");
							// 每当下载的缓存接近buff.length，或是下载完毕时，将buff中的数据插入缓存文件
							if (mCacheFile.insert(mRangeStart - hasDownloadLength, buff, hasDownloadLength)) {
								needDownloadLength -= hasDownloadLength;
								hasDownloadLength = 0;
							} else {
                                Log.e(TAG, "将网络数据插入缓存文件时失败，跳出循环，再次分析");
								break;
							}
						}
					} else {
						Log.e(TAG, "缓存控制信息告诉我还能从网络读数据，可流的结束已到达，将缓冲区里的数据插入缓存文件，再结束");
                        mCacheFile.insert(mRangeStart - hasDownloadLength, buff, hasDownloadLength);
                        return;
					}
				}
			}
		}
	}

	private void processRequestWithoutCache() throws IOException {
		httpConnect();
		int contentSize = HttpUtils.getContentSize(mConnection);
		// 返回请求的响应头
		sendResponseHeader(mRangeStart, contentSize - 1, contentSize);
		byte[] buff = new byte[40 * 1024];
		int readBytes;
		while (mRunnable && (readBytes = mData.read(buff)) != -1) {
            mDataPos += readBytes;
			// 返回请求的数据
			mClient.getOutputStream().write(buff, 0, readBytes);
			if ((int) (Math.random() * 100) == 0) {
				Log.d(TAG, "√√√↑↑↑-- READ 网络 length:" + readBytes + "  " + mRangeStart + "-" + (mRangeStart + readBytes - 1) + " --↑↑↑√√√");
			}
			mRangeStart += readBytes;
			if (mRequestListener != null) {
				mRequestListener.onWriteIntoClient((float) mRangeStart / contentSize);
			}
		}
	}

	private void httpConnect() throws IOException {
		if (mRangeStart != mDataPos) {
			HttpURLConnection con = (HttpURLConnection) mConnection.getURL().openConnection();
			// 取消gzip数据压缩，避免内容长度不准确
			con.setRequestProperty("Accept-Encoding", "identity");
			// 添加设置了start的Range，以改变读取位置
			con.setRequestProperty("Range", "bytes=" + mRangeStart + "-");
			con.setConnectTimeout(10000);
			con.setReadTimeout(30000);
			if (mData != null) {
				mData.close();
			}
			mConnection.disconnect();
			mConnection = con;
			Log.d(TAG, "为了改变读取位置，创建了新连接，更替原连接");
		}
		mConnection.connect();
		int code = mConnection.getResponseCode();
		if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
			throw new IOException("responseCode=" + code + " URL->" + mConnection.getURL());
		}
		mData = mConnection.getInputStream();
		mDataPos = mRangeStart;
	}

	/**
	 * 伪造Response Header并发送
	 *
	 * @param rangeStart 数据起始位置（如果从头开始则为0）
	 * @param rangeEnd 数据截止位置（一般为文件长度-1）
	 * @param fileSize 请求的文件长度
	 * @throws IOException
	 */
	private void sendResponseHeader(int rangeStart, int rangeEnd, int fileSize) throws IOException {
		String httpString = genResponseHeader(rangeStart, rangeEnd, fileSize);
		byte[] httpHeader = httpString.toString().getBytes();
		mClient.getOutputStream().write(httpHeader);
		Log.d(TAG, "--- WriteHeader ---\n" + httpString);
	}

	/**
	 * 生成返回MediaPlayer的Response Header
	 */
	private String genResponseHeader(int rangeStart, int rangeEnd, int fileSize) {
		StringBuffer sb = new StringBuffer();
		sb.append("HTTP/1.1 206 Partial Content").append("\n");
		sb.append("Content-Type: audio/mpeg").append("\n");
		sb.append("Content-Length: ").append(rangeEnd - rangeStart + 1).append("\n");
		sb.append("Connection: keep-alive").append("\n");
		sb.append("Accept-Ranges: bytes").append("\n");
		String contentRangeValue = String.format("bytes " + "%d-%d/%d", rangeStart, rangeEnd, fileSize);
		sb.append("Content-Range: ").append(contentRangeValue).append("\n");
		sb.append("\n");
		return sb.toString();
	}

	/**
	 * 运行在MediaRequestThread请求线程
	 */
	public interface RequestListener {
		void onWriteIntoClient(float progress);
	}

	/**
	 * 运行在MediaRequestThread请求线程
	 */
	public interface RequestErrorListener {
		void onNetworkError();
		void onUrlInvalidError();
		void onTimeoutError();
	}
}
