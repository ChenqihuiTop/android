package com.cqh.android.media;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnSeekCompleteListener;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.widget.SeekBar;

import java.io.IOException;

public class MediaCachePlayer implements OnBufferingUpdateListener, OnCompletionListener, OnPreparedListener, OnSeekCompleteListener, MediaPlayer.OnErrorListener,
		MediaRequestThread.RequestListener {
	private static final String TAG = MediaCachePlayer.class.getSimpleName();

	private Context mContext;
	private MediaClientProxy mediaClientProxy;

	private MediaPlayer mMediaPlayer;
	private SeekBar mSeekBar;

	private String mUrlString;
	private String mFileName;
	private boolean mCacheable;

	private boolean mHasSetDataSource;
	private boolean mPreparing;
	private boolean mPrepared;
	private boolean mAutoStartOnPrepared;

	private MediaCachePlayerListener mMediaCachePlayerListener;

	public MediaCachePlayer(Context context) {
		mContext = context;
		mediaClientProxy = new MediaClientProxy(context, this);
		mediaClientProxy.startProxy();
		mediaClientProxy.setRequestListener(this);
	}

	public String getUrlString() {
		return mUrlString;
	}
	public String getFileName() {
		return mFileName;
	}

	public boolean isPreparing() {
		return mPreparing;
	}
	public boolean isPrepared() {
		return mPrepared;
	}

	public void setMediaCachePlayerListener(MediaCachePlayerListener mediaCachePlayerListener) {
		mMediaCachePlayerListener = mediaCachePlayerListener;
	}
	public void setRequestErrorListener(MediaRequestThread.RequestErrorListener listener) {
		mediaClientProxy.setRequestErrorListener(listener);
	}

	public void setDataSourceAndPrepareAsync(String urlString) {
		setDataSourceAndPrepareAsync(urlString, true);
	}
	public void setDataSourceAndPrepareAsync(String urlString, boolean cacheable) {
		setDataSource(urlString, cacheable);
		if (mHasSetDataSource) {
			mPreparing = true;
			mMediaPlayer.prepareAsync();
			mMediaCachePlayerListener.onPrepare(true);
		}
	}

	public void setDataSource(String urlString) {
		setDataSource(urlString, true);
	}
	public void setDataSource(String urlString, boolean cacheable) {
		mediaClientProxy.interruptCurrentRequestThread();
		if (mMediaPlayer == null || !mHasSetDataSource || mPreparing) {
			// 当MediaPlayer设置源失败 或者 在上一个源开始准备后还没成功，为了避免对下一个源产生影响，销毁它并重新实例化
			releasePlayer();
			mMediaPlayer = new MediaPlayer();
			mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			mMediaPlayer.setOnPreparedListener(this);
			mMediaPlayer.setOnBufferingUpdateListener(this);
			mMediaPlayer.setOnSeekCompleteListener(this);
			mMediaPlayer.setOnCompletionListener(this);
			mMediaPlayer.setOnErrorListener(this);
			Log.d(TAG, "MediaPlayer new");
		} else {
			mMediaPlayer.reset();
			Log.d(TAG, "MediaPlayer reset");
		}

		mHasSetDataSource = false;
		mPreparing = false;
		mPrepared = false;
		mAutoStartOnPrepared = false;

		mUrlString = urlString;
		mFileName = FileUtils.getValidFileName(urlString);
		mCacheable = cacheable;
		mediaClientProxy.setCacheable(cacheable);

		resetSeekBar();

		if (!TextUtils.isEmpty(urlString)) {
			try {
				mMediaPlayer.setDataSource(mediaClientProxy.getProxyURL(urlString));
				mHasSetDataSource = true;
			} catch (IOException e) {
				Log.e(TAG, "播放器设置源失败", e);
			}
		}
		mMediaCachePlayerListener.onSetDataSource(mHasSetDataSource);
	}

	private void releasePlayer() {
		if (mMediaPlayer != null) {
			if (mMediaPlayer.isPlaying()) {
				mMediaPlayer.stop();
			}
			mMediaPlayer.release();
			mMediaPlayer = null;
		}
	}

	public void resetSeekBar() {
		if (mSeekBar != null) {
			currentProgressDecimal = 0;
			playerBufferingProgressDecimal = 0;
			mSeekBar.setProgress(0);
			updateSeekBarSecondaryProgress();
			handle.sendEmptyMessage(MSG_UPDATE_SEEKBAR);
		}
	}

	public void setSeekBar(SeekBar seekBar) {
		mSeekBar = seekBar;
		mSeekBar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
	}

	private void updateSeekBarSecondaryProgress() {
		// 缓冲进度选取播放器缓冲进度和本地缓存进度中的最大值
		if ((int) (playerBufferingProgressDecimal + 0.005f) == 1) {
			mSeekBar.setSecondaryProgress(mSeekBar.getMax());
		} else {
			float cacheBufferingProgressDecimal = 0;
			if (mCacheable) {
				cacheBufferingProgressDecimal = MediaCacheFile.getBufferingProgress(mContext, mFileName, currentProgressDecimal);
			}
			mSeekBar.setSecondaryProgress((int) (mSeekBar.getMax() * Math.max(playerBufferingProgressDecimal, cacheBufferingProgressDecimal) + 0.5f));
		}
	}


	// 通过handler更新进度条只能增加进度
	private float currentProgressDecimal;
	// 播放器缓冲进度
	private float playerBufferingProgressDecimal;

	private static final int MSG_UPDATE_SEEKBAR = 1;
	private static final int MSG_UPDATE_SEEKBAR_SECONDARY_PROGRESS_ONLY = 2;
	private Handler handle = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case MSG_UPDATE_SEEKBAR:
					if (mMediaPlayer != null) {
						int position = 0;
						if (mSeekBar != null) {
							if (mPrepared && mMediaPlayer.isPlaying()) {
								position = mMediaPlayer.getCurrentPosition();
								int duration = mMediaPlayer.getDuration();
								float progress = (float) position / duration;
								if (duration < 0 || progress <= currentProgressDecimal) {
									mMediaCachePlayerListener.onSeekBarProgressStagnant(mPreparing, mMediaPlayer.isPlaying());
								} else {
									if (!mSeekBar.isPressed()) {
										mSeekBar.setProgress((int) (mSeekBar.getMax() * progress));
									}
									currentProgressDecimal = progress;
								}
							} else {
								mMediaCachePlayerListener.onSeekBarProgressStagnant(mPreparing, mMediaPlayer.isPlaying());
							}
							updateSeekBarSecondaryProgress();
						}
						handle.removeMessages(MSG_UPDATE_SEEKBAR);
						int delayMillis = Math.max(1000 - position % 1000, 500);
						handle.sendEmptyMessageDelayed(MSG_UPDATE_SEEKBAR, delayMillis);
					}
					break;
				case MSG_UPDATE_SEEKBAR_SECONDARY_PROGRESS_ONLY:
					updateSeekBarSecondaryProgress();
					break;
			}
		}
	};


	/**
	 * if isPrepared && hasSetDataSource && !isPreparing then prepareAsync()
     * if it can't start right now, it will auto start on prepared
	 */
	public void start() {
		if (mMediaPlayer == null) {
			setDataSourceAndPrepareAsync(mUrlString);
		}
		if (mMediaPlayer != null) {
			if (mPrepared) {
				mMediaPlayer.start();
				handle.sendEmptyMessageDelayed(MSG_UPDATE_SEEKBAR, 100);
			} else {
				if (mHasSetDataSource && !mPreparing) {
					mPreparing = true;
					mMediaPlayer.prepareAsync();
					mMediaCachePlayerListener.onPrepare(true);
				}
				mAutoStartOnPrepared = true;
			}
		}
	}

	public void pause() {
		if (mMediaPlayer == null) {
			setDataSourceAndPrepareAsync(mUrlString);
		}
		if (mMediaPlayer != null) {
			if (mPrepared) {
				if (mMediaPlayer.isPlaying()) {
					mMediaPlayer.pause();
				}
			} else {
				mAutoStartOnPrepared = false;
			}
		}
	}

	public boolean isPlaying() {
		if (mMediaPlayer != null) {
			return mMediaPlayer.isPlaying();
		}
		return false;
	}

	public void release() {
		releasePlayer();
		resetSeekBar();
		mediaClientProxy.stopProxy();
	}


	SeekBar.OnSeekBarChangeListener mOnSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
		private float seekBarProgressDecimalFromUser;
		@Override
		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			float progressDecimal = (float) progress / seekBar.getMax();
			if (fromUser) {
				seekBarProgressDecimalFromUser = progressDecimal;
			}
			mMediaCachePlayerListener.onSeekBarProgressChange(progressDecimal, fromUser);
		}
		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {}
		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
			// seekTo()的参数是相对与影片时间的数字，而不是与seekBar.getMax()相对的数字
			currentProgressDecimal = seekBarProgressDecimalFromUser;
			updateSeekBarSecondaryProgress();
			if (mPrepared) {
				int pos = (int) (mMediaPlayer.getDuration() * currentProgressDecimal);
				mMediaPlayer.seekTo(pos);
				mMediaCachePlayerListener.onSeekTo(pos);
				handle.sendEmptyMessageDelayed(MSG_UPDATE_SEEKBAR, 100);
			}
			Log.d(TAG, "onStopTrackingTouch " + currentProgressDecimal);
		}
	};

	@Override
	public void onPrepared(MediaPlayer mediaPlayer) {
		Log.d(TAG, mUrlString + "\nonPrepared");
		mPreparing = false;
		mPrepared = true;
		mMediaCachePlayerListener.onPrepared(mediaPlayer.getDuration(), mAutoStartOnPrepared);
		if (currentProgressDecimal > 0) {
			int pos = (int) (mMediaPlayer.getDuration() * currentProgressDecimal);
			mediaPlayer.seekTo(pos);
			mMediaCachePlayerListener.onSeekTo(pos);
			handle.sendEmptyMessageDelayed(MSG_UPDATE_SEEKBAR, 100);
		}
		if (mAutoStartOnPrepared) {
			mediaPlayer.start();
		}
	}

	@Override
	public void onBufferingUpdate(MediaPlayer mediaPlayer, int bufferingProgress) {
		float playProgress = 0;
		if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
			if (mPrepared) {
				playProgress = (float) mediaPlayer.getCurrentPosition() / mediaPlayer.getDuration();
			}
			playerBufferingProgressDecimal = playProgress + (float) bufferingProgress / 100;
		} else {
			playerBufferingProgressDecimal = (float) bufferingProgress / 100;
		}
	}

	@Override
	public void onSeekComplete(MediaPlayer mp) {
		Log.d(TAG, mUrlString + "\nonSeekComplete");
		mMediaCachePlayerListener.onSeekComplete();
	}

	@Override
	public void onCompletion(MediaPlayer mediaPlayer) {
		Log.d(TAG, mUrlString + "\nonCompletion");
		resetSeekBar();
		mMediaCachePlayerListener.onCompletion(mPrepared);
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		Log.e(TAG, mUrlString + "\nMediaPlayer onError(" + what + ", " + extra + ")");
		mMediaCachePlayerListener.onError(what, extra);
		// return true可以代处理Error，这样Error后MediaPlayer就不会调用OnCompletionListener
		return true;
	}

	@Override
	public void onWriteIntoClient(float progress) {
		if (progress - playerBufferingProgressDecimal > 0.005) {
			playerBufferingProgressDecimal = progress;
			handle.sendEmptyMessage(MSG_UPDATE_SEEKBAR_SECONDARY_PROGRESS_ONLY);
		}
	}

	/**
	 * 运行在MediaCachePlayer所在进程
	 */
	public interface MediaCachePlayerListener {
		void onSetDataSource(boolean isSuccessful);
		void onPrepare(boolean isAsync);
		void onPrepared(int duration, boolean autoStart);
		void onSeekBarProgressChange(float progressDecimal, boolean fromUser);
		void onSeekBarProgressStagnant(boolean isPreparing, boolean isPlaying);
		void onSeekTo(int pos);
		void onSeekComplete();
		void onCompletion(boolean isPrepared);
		void onError(int what, int extra);
	}
}
