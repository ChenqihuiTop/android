package com.cqh.android.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.cqh.android.R;
import com.cqh.android.media.FileUtils;
import com.cqh.android.media.MediaCachePlayer;
import com.cqh.android.media.MediaPreCacheThread;
import com.cqh.android.media.MediaRequestThread;
import com.cqh.android.util.Utils;

import java.util.ArrayList;


public class AudioActivity extends Activity implements OnClickListener {
    private static final String TAG = AudioActivity.class.getSimpleName();

    private TextView name, currentTime, totalTime;
    private Button prev, start_or_pause, next;

    private SeekBar seek_bar;
    private Drawable seekBarDrawable;
    private AnimationDrawable seekBarLoadingDrawable;

    private MediaCachePlayer mediaCachePlayer;

    ArrayList<String> urlStrings;
    int index;

    @SuppressLint("NewApi")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio);

        initView();
        initData();
        initControl();
    }

    private void initView() {
        name = (TextView) findViewById(R.id.name);
        currentTime = (TextView) findViewById(R.id.currentTime);
        totalTime = (TextView) findViewById(R.id.totalTime);
        seek_bar = (SeekBar) findViewById(R.id.seek_bar);
        prev = (Button) findViewById(R.id.prev);
        start_or_pause = (Button) findViewById(R.id.start_or_pause);
        next = (Button) findViewById(R.id.next);
    }

    private void initData() {
        seekBarDrawable = getResources().getDrawable(R.mipmap.seekbar_thumb);
        seekBarLoadingDrawable = (AnimationDrawable) getResources().getDrawable(R.drawable.seekbar_thumb_load);
        seek_bar.setOnTouchListener(mOnSeekBarTouchListener);
        mediaCachePlayer = new MediaCachePlayer(this);
        mediaCachePlayer.setSeekBar(seek_bar);
        mediaCachePlayer.setMediaCachePlayerListener(mediaCachePlayerListener);
        mediaCachePlayer.setRequestErrorListener(requestErrorListener);


        urlStrings = new ArrayList<String>();
        urlStrings.add("http://172.3.32.6/data2/music/241823393/2418233963.mp3?xcode=5733bb91209a803152d17b2d9b423fd1");
//        urlStrings.add("http://zhangmenshiting.baidu.com/data2/music/241823393/241823393.mp3");
        urlStrings.add("http://zhangmenshiting.baidu.com/data2/music/909b37522bde6d2c3fbb1391406f2894/310718576/310718576.mp3?xcode=dc29226ca2327570dcd0b1435228577d");
        urlStrings.add("http://ugc.cdn.qianqian.com/yinyueren/audio/aac2b3c2d1f89bc91c23f58ce53811eb.mp3");
        urlStrings.add("http://ugc.cdn.qianqian.com/yinyueren/audio/7d2d57805adfc1dcbefda690cad840c2.mp3");
        urlStrings.add("http://ugc.cdn.qianqian.com/yinyueren/audio/5b2a8b2280e3d4a63aa4ed223bc49993.mp3");
        urlStrings.add("http://ugc.cdn.qianqian.com/yinyueren/audio/6e18010317e36e294c4556e8e79f4ad6.mp3");
        index = 0;
        setSongWithPrepare(index);
    }

    private void initControl() {
        prev.setOnClickListener(this);
        start_or_pause.setOnClickListener(this);
        next.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.prev:
                index = (index + urlStrings.size() - 1) % urlStrings.size();
                setSongWithPrepare(index);
                mediaCachePlayer.start();
                UpdatePlayerStartUI();
                break;
            case R.id.start_or_pause:
                if ("start".equals(start_or_pause.getTag())) {
                    mediaCachePlayer.start();
                    UpdatePlayerStartUI();
                } else {
                    mediaCachePlayer.pause();
                    UpdatePlayerPauseUI();
                }
                break;
            case R.id.next:
                index = (index + 1) % urlStrings.size();
                setSongWithPrepare(index);
                mediaCachePlayer.start();
                UpdatePlayerStartUI();
                break;
        }
    }


    private void setSongWithPrepare(int index) {
        mediaCachePlayer.setDataSourceAndPrepareAsync(urlStrings.get(index));
        name.setText(FileUtils.getValidFileName(urlStrings.get(index)));
    }

    private void setSong(int index) {
        mediaCachePlayer.setDataSource(urlStrings.get(index));
        name.setText(FileUtils.getValidFileName(urlStrings.get(index)));
    }

    private void preCacheSong(int index) {
        if (index >= 0 && index < urlStrings.size()) {
            MediaPreCacheThread.preLoad(this, urlStrings.get(index));
        }
    }

    private void UpdatePlayerStartUI() {
        start_or_pause.setTag("pause");
        start_or_pause.setText("暂停");
    }

    private void UpdatePlayerPauseUI() {
        start_or_pause.setTag("start");
        start_or_pause.setText("播放");
    }

    private boolean mIsLoading;
    private void updateSeekBarUI(boolean isLoading) {
        if (isLoading) {
            if (!mIsLoading) {
                seek_bar.setThumb(seekBarLoadingDrawable);
                seekBarLoadingDrawable.start();
                seekBarLoadingDrawable.setVisible(true, false);
            }
        } else {
            if (mIsLoading) {
                seek_bar.setThumb(seekBarDrawable);
                seekBarLoadingDrawable.setVisible(false, false);
            }
        }
        mIsLoading = isLoading;
    }

    private static final int MSG_PLAYER_NETWORK_ERROR = 1;
    private static final int MSG_PLAYER_URL_INVALID_ERROR = 2;
    private static final int MSG_PLAYER_TIMEOUT_ERROR = 3;
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_PLAYER_NETWORK_ERROR:
                    Utils.showToast("无可用网络", Gravity.CENTER);
                    setSong(index);
                    UpdatePlayerPauseUI();
                    break;
                case MSG_PLAYER_URL_INVALID_ERROR:
                    Utils.showToast("链接失效", Gravity.CENTER);
                    setSong(index);
                    UpdatePlayerPauseUI();
                    break;
                case MSG_PLAYER_TIMEOUT_ERROR:
                    Utils.showToast("网络不给力，播放取消", Gravity.CENTER);
                    setSong(index);
                    UpdatePlayerPauseUI();
                    break;

            }
        }
    };

    SeekBar.OnTouchListener mOnSeekBarTouchListener = new View.OnTouchListener() {
        // 使SeekBar只能拖动改变进度，不能点击改变进度
        boolean isActionAllowed;
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (v instanceof SeekBar) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    SeekBar seekBar = (SeekBar) v;
                    int buttonX = seekBar.getPaddingLeft() + (seekBar.getWidth() - seekBar.getPaddingLeft() - seekBar.getPaddingRight()) * seekBar.getProgress() / seekBar.getMax();
                    if (Math.abs(event.getX() - buttonX) > seekBar.getPaddingLeft()) {
                        isActionAllowed = false;
                    } else {
                        isActionAllowed = true;
                    }
                }
                return !isActionAllowed;
            }
            return false;
        }
    };

    MediaCachePlayer.MediaCachePlayerListener mediaCachePlayerListener = new MediaCachePlayer.MediaCachePlayerListener() {
        int duration;
        @Override
        public void onSetDataSource(boolean isSuccessful) {
            if (!isSuccessful) {
                Utils.showToast("链接非法不可用", Gravity.CENTER);
            }
            updateSeekBarUI(false);
        }
        @Override
        public void onPrepare(boolean isAsync) {
            updateSeekBarUI(true);
        }
        @Override
        public void onPrepared(int duration, boolean autoStartOnPrepared) {
            this.duration = duration;
            totalTime.setText(Utils.parseMilliseconds(duration));
            preCacheSong(index + 1);
            updateSeekBarUI(false);
        }
        @Override
        public void onSeekBarProgressChange(float progressDecimal, boolean fromUser) {
            int pos = (int) (duration * progressDecimal);
            currentTime.setText(Utils.parseMilliseconds(pos));
            if (!fromUser) {
                updateSeekBarUI(false);
            }
        }
        @Override
        public void onSeekBarProgressStagnant(boolean isPreparing, boolean isPlaying) {
            if (isPreparing || isPlaying) {
                updateSeekBarUI(true);
            } else {
                updateSeekBarUI(false);
            }
        }
        @Override
        public void onSeekTo(int pos) {}
        @Override
        public void onSeekComplete() {}
        @Override
        public void onCompletion(boolean isPrepared) {
            if (isPrepared) {
                // 准备好后完成，才应该按正常播放完成处理
//                index = (index + 1) % urlStrings.size();
//                setSongWithPrepare(index);
//                mediaCachePlayer.start();
//                UpdatePlayerStartUI();
            }
        }
        @Override
        public void onError(int what, int extra) {
            setSong(index);
            UpdatePlayerPauseUI();
        }
    };

    MediaRequestThread.RequestErrorListener requestErrorListener = new MediaRequestThread.RequestErrorListener() {
        @Override
        public void onNetworkError() {
            handler.sendEmptyMessage(MSG_PLAYER_NETWORK_ERROR);
        }
        @Override
        public void onUrlInvalidError() {
            handler.sendEmptyMessage(MSG_PLAYER_URL_INVALID_ERROR);
        }
        @Override
        public void onTimeoutError() {
            handler.sendEmptyMessage(MSG_PLAYER_TIMEOUT_ERROR);
        }
    };

}
