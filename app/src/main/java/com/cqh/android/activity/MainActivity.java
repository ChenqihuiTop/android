package com.cqh.android.activity;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.cqh.android.R;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private Button audio_action;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        initData();
        initControl();
    }

    private void initView() {
        audio_action = (Button) findViewById(R.id.audio_action);
    }

    private void initData() {

    }

    private void initControl() {
        audio_action.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.audio_action:
                startActivity(new Intent(MainActivity.this, AudioActivity.class));
                break;
        }
    }
}
