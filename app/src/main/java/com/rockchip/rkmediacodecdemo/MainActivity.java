package com.rockchip.rkmediacodecdemo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {
    Button start_button;
    private PlayerView mSurfaceView;
    private PlayerView mSurfaceView2;
    private PlayerView mSurfaceView3;
    private PlayerView mSurfaceView4;
    //    private String RTSP_IP1 = "172.16.9.113";
    private String RTSP_IP1 = "192.168.17.108";
    private String RTSP_IP2 = "192.168.17.101";
    private String RTSP_IP3 = "192.168.17.105";
    private String RTSP_IP4 = "192.168.17.106";
    private int RTSP_port2 = 8554;
    private int RTSP_port = 554;
    private String RTSP_func2 = "video";
    private String RTSP_func = "live/av0";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSurfaceView = (PlayerView) findViewById(R.id.surfaceView1);
        mSurfaceView2 = (PlayerView) findViewById(R.id.surfaceView2);
        mSurfaceView3 = (PlayerView) findViewById(R.id.surfaceView3);
        mSurfaceView4 = (PlayerView) findViewById(R.id.surfaceView4);

        start_button = (Button) findViewById(R.id.start_button);
        start_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View    view) {
                Log.d("*******", "onClick: ----------------------------------");
                mSurfaceView.MediaPaly(RTSP_IP1, RTSP_port2, RTSP_func2);
//                mSurfaceView.MediaPaly(RTSP_IP1,RTSP_port,RTSP_func);
                mSurfaceView2.MediaPaly(RTSP_IP3, RTSP_port, "live/av1");
//                mSurfaceView2.MediaPaly(RTSP_IP2, RTSP_port, RTSP_func);
                mSurfaceView3.MediaPaly(RTSP_IP3,RTSP_port,RTSP_func);
                mSurfaceView4.MediaPaly(RTSP_IP4,RTSP_port,RTSP_func);
            }
        });
    }

}
