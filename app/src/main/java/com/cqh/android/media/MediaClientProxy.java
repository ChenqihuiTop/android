package com.cqh.android.media;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.StringTokenizer;

/**
 * 代理播放器客户端发出的请求
 */
public class MediaClientProxy implements Runnable {
	private static final String TAG = MediaClientProxy.class.getSimpleName();

	private ServerSocket mServerSocket;
	private int mPort;

	private Context mContext;
    private MediaCachePlayer mPlayer;
    private Thread mProxyThread;

	private MediaRequestThread mRequestThread;
	private boolean mCacheable;
	private MediaRequestThread.RequestListener mRequestListener;
	private MediaRequestThread.RequestErrorListener mRequestErrorListener;

	public MediaClientProxy(Context context, MediaCachePlayer player) {
		mContext = context;
		mPlayer = player;
	}

	public void setRequestListener(final MediaRequestThread.RequestListener listener) {
		mRequestListener = listener;
		if (mRequestThread != null) {
			mRequestThread.setRequestListener(listener);
		}
	}
	public void setRequestErrorListener(final MediaRequestThread.RequestErrorListener listener) {
		mRequestErrorListener = listener;
		if (mRequestThread != null) {
			mRequestThread.setRequestErrorListener(listener);
		}
	}

	public boolean startProxy() {
		if (mPlayer != null && (isServerSocketAvailable() || initServerSocket())) {
			if (mProxyThread == null || !mProxyThread.isAlive()) {
				mProxyThread = new Thread(this);
				mProxyThread.start();
				Log.d(TAG, "ProxyThread id=" + mProxyThread.getId() + " start");
			}
			return true;
		}
		return false;
	}

	private boolean isServerSocketAvailable() {
		if (mServerSocket != null && mServerSocket.isBound() && !mServerSocket.isClosed()) {
			return true;
		}
		return false;
	}

	private boolean initServerSocket() {
		try {
			if (mServerSocket != null && !mServerSocket.isClosed()) {
				mServerSocket.close();
			}
			mServerSocket = new ServerSocket(mPort, 0, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
			mPort = mServerSocket.getLocalPort();
			Log.d(TAG, "ServerSocket mPort " + mPort + " initializing");
		} catch (IOException e) {
			Log.e(TAG, "Error initializing mServerSocket", e);
			return false;
		}
		return true;
	}

	public void stopProxy() {
		interruptCurrentRequestThread();
		if (mProxyThread != null) {
			mProxyThread.interrupt();
			Log.d(TAG, "ProxyThread id=" + mProxyThread.getId() + " ready to stop");
			mProxyThread = null;
		}
		if (mServerSocket != null) {
			try {
				mServerSocket.close();
				mServerSocket = null;
				Log.d(TAG, "ServerSocket mPort " + mPort + " close");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void setCacheable(boolean cacheable) {
		mCacheable = cacheable;
	}

	public String getProxyURL(String url) {
		if (startProxy()) {
			return String.format("http://127.0.0.1:%d/%s", mPort, url);
		}
		return url;
	}

	public void interruptCurrentRequestThread() {
		if (mRequestThread != null) {
			mRequestThread.setRequestListener(null);
			mRequestThread.setRequestErrorListener(null);
			mRequestThread.setRunnable(false);
			mRequestThread = null;
		}
	}

	@Override
	public void run() {
		Log.d(TAG, "ProxyThread id=" + mProxyThread.getId() + " running");
		while (Thread.currentThread() == mProxyThread && !Thread.currentThread().isInterrupted()) {
			try {
				if (!isServerSocketAvailable() && !initServerSocket()) {
					return;
				}
				final Socket client = mServerSocket.accept();
				if (client == null || (!mPlayer.isPreparing() && !mPlayer.isPrepared())) {
					// 播放器还没开始准备时不会发送请求，此时捕获了请求也不处理
					continue;
				}
				HttpURLConnection connection = readRequest(client);
				if (connection != null && checkUrlTimeliness(connection)) {
					interruptCurrentRequestThread();
					mRequestThread = new MediaRequestThread(mContext, client, connection, mCacheable, mRequestListener, mRequestErrorListener);
					Log.i(TAG, "================ MediaClientProxy捕获了一个播放器请求并开启处理线程 ================ " + mRequestThread.getId());
					mRequestThread.start();
				}
			} catch (IOException e) {
				Log.e(TAG, "Error connecting to client", e);
			}
		}
		Log.d(TAG, "ProxyThread id=" + mProxyThread.getId() + " interrupted and shutting down");
	}


	/**
	 * 客户端代理根据请求的URL文件名来确定该请求的时效性（与当前播放的音频是否一致），以决定是否处理该请求
	 * @param connection 捕获的请求
	 * @return 该请求的时效性
	 */
	private boolean checkUrlTimeliness(HttpURLConnection connection) {
		if (mPlayer == null)
			return false;
		String playerFileName = mPlayer.getFileName();
		String connectionFileName = FileUtils.getValidFileName(connection.getURL());
		return TextUtils.equals(playerFileName, connectionFileName);
	}

	private HttpURLConnection readRequest(Socket client) {
		// 得到Request String
		HttpURLConnection connection = null;
		int bytes_read;
		byte[] local_request = new byte[1024];
		String requestStr = "";
		try {
			while ((bytes_read = client.getInputStream().read(local_request)) != -1) {
				requestStr += new String(local_request, 0, bytes_read);
				if (requestStr.contains("GET") && requestStr.contains("\r\n\r\n")) {
					break;
				}
			}
		} catch (IOException e) {
			Log.e(TAG, "获取请求头异常", e);
			return connection;
		}

		if (TextUtils.isEmpty(requestStr)) {
			Log.i(TAG, "请求头为空，获取异常");
			return connection;
		}

		Log.d(TAG, requestStr);
		// 将Request String组合为HttpUriRequest
		String[] requestParts = requestStr.split("\r\n");
		StringTokenizer st = new StringTokenizer(requestParts[0]);
		String method = st.nextToken();
		String url = st.nextToken().substring(1);

		try {
			connection = (HttpURLConnection) new URL(url).openConnection();
		} catch (IOException e) {
			Log.e(TAG, "open connection fail", e);
			return connection;
		}
		for (int i = 1; i < requestParts.length; i++) {
			int separatorLocation = requestParts[i].indexOf(":");
    		String name = requestParts[i].substring(0, separatorLocation).trim();
			String value = requestParts[i].substring(separatorLocation + 1).trim();
			// 不添加Host Header，因为URL的Host为127.0.0.1
			if (!"Host".equals(name)) {
				connection.setRequestProperty(name, value);
			}
		}
		// 取消gzip数据压缩，避免内容长度不准确
		connection.setRequestProperty("Accept-Encoding", "identity");
		// 如果没有Range，统一添加默认Range,方便后续处理
		if (connection.getRequestProperty("Range") == null) {
			connection.setRequestProperty("Range", "bytes=0-");
		}
		connection.setConnectTimeout(10000);
		connection.setReadTimeout(30000);
		return connection;
	}

}