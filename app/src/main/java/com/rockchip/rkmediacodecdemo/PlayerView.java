package com.rockchip.rkmediacodecdemo;

import android.content.Context;
import android.opengl.GLES20;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Created by cxh on 2018/1/3
 * E-mail: shon.chen@rock-chips.com
 */

public class PlayerView extends SurfaceView implements SurfaceHolder.Callback{

    static final String TAG = "PlayerView";
    Context mContext;
    private RKMediaplayer mRKMediaplayer = null;
    private SurfaceHolder mSurfaceHolder;
    public PlayerView(Context context) {
        super(context);
        mContext = context;
        init();
    }

    private void init() {
        mSurfaceHolder = getHolder();
        mSurfaceHolder.addCallback(this);
    }
    public PlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init();
    }

    public PlayerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        init();
    }


    public String getfps(){
        if (mRKMediaplayer != null) {
            return mRKMediaplayer.getfps();
        }else {
            Log.d(TAG, "MediaPaly: NULLLLLLLLLLLLLLLLLLLLLLLLLLLL");
        }
        return null;
    }

    /**
     * 以下为播放器状态控制代码
     */
    public void MediaPaly(String RTSP, int RTSPport, String RTSPfunc) {
        //初始化原本应该得放在OpenGL创建的时候一起的，但是如果在OpenGL初始化的时候创建就会出现闪退的现象
        //GLSurfaceView_num > 1 说明是只有一个播放窗口需要为其添加一个播放器
        if (mRKMediaplayer == null  ){
            mRKMediaplayer = new RKMediaplayer(mSurfaceHolder);
        }
        mRKMediaplayer.playRTSPVideo(RTSP, RTSPport, RTSPfunc);
    }

    public void MediaStop() {
        if (mRKMediaplayer != null) {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            mRKMediaplayer.stop();
        }
    }

    public void MediaPause() {
        if (mRKMediaplayer != null) {
        }
    }

    public void MediaContinue() {
        if (mRKMediaplayer != null) {
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        mRKMediaplayer = new RKMediaplayer(mSurfaceHolder);
//        mRKPlayer = new RKMediaplayer(surfaceHolder, 0);

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        mRKMediaplayer.stop();
    }
}
