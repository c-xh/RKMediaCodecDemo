package com.rockchip.rkmediacodecdemo;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.rockchip.rkmediacodec.RKMediaCodec;
import com.rockchip.rkmediacodecdemo.rtsp.RTPConnector;
import com.rockchip.rkmediacodecdemo.rtsp.RTSPConnector;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static java.lang.Thread.sleep;


/**
 * Created by zhanghao on 2016/12/27.
 */

public class RKMediaplayer {
    private static final String TAG = "RKMediaplayer";
    private static final boolean DEBUG_SAVE_VIDEO_FILE = false;
    private static final boolean DEBUG_SAVE_AUDIO_FILE = false;
    private static final boolean DEBUG_QUEUE_DEQUEUE = false;

    private final int VIDEO_WIDTH = 1920;
    private final int VIDEO_HEIGHT = 1080;
    private final int AUDIO_SAMPLE_RATE = 44100;

    private final String DECODER_H264 = "video/avc";
    private final String DECODER_AAC = "audio/mp4a-latm";

    private Surface mSurface = null;
    private RKMediaCodec mVideoCodec = null;
    private MediaCodec mAudioCodec = null;
    private AudioTrack mAudioTrack = null;

    //private ByteBuffer[] mVideoInputBuffers = null;
    //private ByteBuffer[] mVideoOutputBuffers = null;
    private ByteBuffer[] mAudioInputBuffers = null;
    private ByteBuffer[] mAudioOutputBuffers = null;

    private long mVideoCount = 0;
    private long mAudioCount = 0;

    private HandlerThread mVideoDecoderThread = null;
    private Handler mVideoDecoderHandler = null;
    private HandlerThread mAudioDecoderThread = null;
    private Handler mAudioDecoderHandler = null;

    private FileOutputStream mVideoFileOutputStream = null;
    private FileOutputStream mAudioFileOutputStream = null;

    private RTSPConnector mRtspVideoConn = null;
    private RTSPConnector mRtspAudioConn = null;

    private long mLastTimestamp = 0;
    private int mFrames = 0;
    private float mFps = 0.0f;
    private int mVideoWidth = VIDEO_WIDTH;
    private int mVideoHeight = VIDEO_HEIGHT;


    public RKMediaplayer(SurfaceHolder holder) {
//        Log.d(TAG, "RKMediaplayer: " + mSurfaceTexture);

        mSurface = holder.getSurface();
//        try {
//            mVideoFileOutputStream = new FileOutputStream("/data/data/com.demo.cx1.rbs_ctrl/debug"+getGLViewSurfaceTextureID+".h264");
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }
        mVideoDecoderThread = new HandlerThread(TAG + "Video");
        mVideoDecoderThread.start();
        mVideoDecoderHandler = new Handler(mVideoDecoderThread.getLooper());

        mAudioDecoderThread = new HandlerThread(TAG + "Audio");
        mAudioDecoderThread.start();
        mAudioDecoderHandler = new Handler(mAudioDecoderThread.getLooper());

        try {
            mVideoCodec = RKMediaCodec.createDecoderByType(DECODER_H264);
            MediaFormat mediaFormat = new MediaFormat();
            mediaFormat.setString(MediaFormat.KEY_MIME, DECODER_H264);
            mediaFormat.setInteger(MediaFormat.KEY_WIDTH, VIDEO_WIDTH);
            mediaFormat.setInteger(MediaFormat.KEY_HEIGHT, VIDEO_HEIGHT);
            mVideoCodec.configure(mediaFormat, mSurface, null, 0);

        } catch (IOException e) {
            Log.e(TAG, "Can not create decoder " + DECODER_H264);
            e.printStackTrace();
        }

        try {
            //MediaExtractor extrator = new MediaExtractor();
            //extrator.setDataSource("/tmp/debug.aac");
            //MediaFormat mediaFormat = extrator.getTrackFormat(0);

            mAudioCodec = MediaCodec.createDecoderByType(DECODER_AAC);
            MediaFormat mediaFormat = MediaFormat.createAudioFormat(DECODER_AAC, 44100, 2);
            mediaFormat.setInteger(MediaFormat.KEY_IS_ADTS, 0);
            byte[] bytes = new byte[]{(byte) 0x12, (byte) 0x12};
            ByteBuffer bb = ByteBuffer.wrap(bytes);
            mediaFormat.setByteBuffer("csd-0", bb);
            // Log.d(TAG, "FORMAT::::::>>>  " + mediaFormat.toString());
            mAudioCodec.configure(mediaFormat, null, null, 0);
        } catch (IOException e) {
            Log.e(TAG, "Can not create decoder " + DECODER_AAC);
            e.printStackTrace();
        }

        int bufferSize = AudioTrack.getMinBufferSize(AUDIO_SAMPLE_RATE
                , AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        mAudioTrack = new AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM);

        startvideo();
    }

    public void repaly() {

        mVideoDecoderThread = new HandlerThread(TAG + "Video");
        mVideoDecoderThread.start();
        mVideoDecoderHandler = new Handler(mVideoDecoderThread.getLooper());

        mAudioDecoderThread = new HandlerThread(TAG + "Audio");
        mAudioDecoderThread.start();
        mAudioDecoderHandler = new Handler(mAudioDecoderThread.getLooper());

//        try {
//            mVideoCodec = MediaCodec.createDecoderByType(DECODER_H264);
//            MediaFormat mediaFormat = new MediaFormat();
//            mediaFormat.setString(MediaFormat.KEY_MIME, DECODER_H264);
//            mediaFormat.setInteger(MediaFormat.KEY_WIDTH, VIDEO_WIDTH);
//            mediaFormat.setInteger(MediaFormat.KEY_HEIGHT, VIDEO_HEIGHT);
////            mVideoCodec.configure(mediaFormat, mSurface, null, 0);
//
//        } catch (IOException e) {
//            Log.e(TAG, "Can not create decoder " + DECODER_H264);
//            e.printStackTrace();
//        }

//        try {
//            //MediaExtractor extrator = new MediaExtractor();
//            //extrator.setDataSource("/tmp/debug.aac");
//            //MediaFormat mediaFormat = extrator.getTrackFormat(0);
//
//            mAudioCodec = MediaCodec.createDecoderByType(DECODER_AAC);
//            MediaFormat mediaFormat = MediaFormat.createAudioFormat(DECODER_AAC,44100, 2);
//            mediaFormat.setInteger(MediaFormat.KEY_IS_ADTS, 0);
//            byte[] bytes = new byte[]{(byte) 0x12, (byte)0x12};
//            ByteBuffer bb = ByteBuffer.wrap(bytes);
//            mediaFormat.setByteBuffer("csd-0", bb);
//            // Log.d(TAG, "FORMAT::::::>>>  " + mediaFormat.toString());
//            mAudioCodec.configure(mediaFormat, null, null, 0);
//        } catch (IOException e) {
//            Log.e(TAG, "Can not create decoder " + DECODER_AAC);
//            e.printStackTrace();
//        }

//        int bufferSize = AudioTrack.getMinBufferSize(AUDIO_SAMPLE_RATE
//                , AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
//        mAudioTrack = new AudioTrack(
//                AudioManager.STREAM_VOICE_CALL,
//                AUDIO_SAMPLE_RATE,
//                AudioFormat.CHANNEL_OUT_STEREO,
//                AudioFormat.ENCODING_PCM_16BIT,
//                bufferSize,
//                AudioTrack.MODE_STREAM);
    }

    private void startvideo() {
        // Video
        mVideoCodec.start();
        //mVideoInputBuffers = mVideoCodec.getInputBuffers();
        //mVideoOutputBuffers = mVideoCodec.getOutputBuffers();

        if (DEBUG_SAVE_VIDEO_FILE) {
            try {
                mVideoFileOutputStream = new FileOutputStream("/data/data/com.demo.cx1.rbs_ctrl/debug.h264");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void startaudio() {
        // Audio
        mAudioCodec.start();
        mAudioInputBuffers = mAudioCodec.getInputBuffers();
        mAudioOutputBuffers = mAudioCodec.getOutputBuffers();
        // Log.d(TAG, "init audiocodec, inputbuffer size="+mAudioInputBuffers.length+" ; outputbuffer size="+mAudioOutputBuffers.length);
        mAudioTrack.play();

        if (DEBUG_SAVE_AUDIO_FILE) {
            try {
                mAudioFileOutputStream = new FileOutputStream("/tmp/debug.aac");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        /*try {
            FileInputStream istream = new FileInputStream("/tmp/debug.aac");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }*/
    }

//    public void play(final String uri){
//        mVideoDecoderHandler.post(new Runnable() {
//            @Override
//            public void run() {
//                if (uri.startsWith("rtsp://")) {
////                    playRTSP(uri);
//                } else {
//                    //playLocalVideo(uri);
//                    playLocalAudio(uri);
//                }
//            }
//        });
//    }

    public void playRTSPVideo(final String ip, final int port, final String func) {
        mVideoDecoderHandler.post(new Runnable() {
            @Override
            public void run() {
                //startvideo();
                try {
                    mRtspVideoConn = new RTSPConnector(ip, port, func);
                    mRtspVideoConn.setOnFrameListener(new RTPConnector.OnFrameListener() {
                        @Override
                        public void OnFrame(byte[] buffer) {
                            if (DEBUG_SAVE_VIDEO_FILE) {
                                try {
                                    mVideoFileOutputStream.write(buffer);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

                            Log.d(TAG, "dequeueInputBuffer  ing ... " + buffer.length);
                            int indexInputbuffer = mVideoCodec.dequeueInputBuffer(-1);
                            //Log.d(TAG, "dequeueInputBuffer :" + indexInputbuffer);
                            if (indexInputbuffer >= 0) {
                                ByteBuffer inputbuffer = mVideoCodec.getInputBuffer(indexInputbuffer);
                                Log.d(TAG, "getinputbuffer : " + indexInputbuffer);
                                //mVideoInputBuffers[indexInputbuffer].clear();
                                inputbuffer.put(buffer);
                                mVideoCodec.queueInputBuffer(indexInputbuffer, 0, buffer.length, ++mVideoCount, 0);
                            }

                            int indexOutputbuffer; //= mVideoCodec.dequeueOutputBuffer(new MediaCodec.BufferInfo(), 0);
                            //Log.d(TAG, "dequeueOutputBuffer  ing ... ");

                            while ((indexOutputbuffer = mVideoCodec.dequeueOutputBuffer(new RKMediaCodec.BufferInfo(), 0)) >= 0) {
                            //if (indexOutputbuffer >= 0) {
                                //Log.d(TAG, "DequeueOutputBuffer success : " + indexOutputbuffer);
                                mVideoCodec.releaseOutputBuffer(indexOutputbuffer, true);
                                ++mFrames;
                                long curtime = System.currentTimeMillis();
                                if (curtime - mLastTimestamp >= 1000) {
                                    mFps = mFrames;
                                    mFrames = 0;
                                    mLastTimestamp = curtime;
                                }
                                //break;
                            }

                            if (indexOutputbuffer == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                MediaFormat format = mVideoCodec.getOutputFormat();
                                if (DEBUG_QUEUE_DEQUEUE)
                                    Log.d(TAG, " format changed : " + indexOutputbuffer + " : " + format);

                                mVideoWidth = format.getInteger(MediaFormat.KEY_WIDTH);
                                mVideoHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
                            } else if (indexOutputbuffer == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                if (DEBUG_QUEUE_DEQUEUE)
                                    Log.d(TAG, " try again later : " + indexOutputbuffer);
                            } else {
                                Log.d(TAG, "indexOutputbuffer = " + indexOutputbuffer);
                            }
                        }
                    });

                    PortScanner mPortScanner = new PortScanner();

                    mRtspVideoConn.requestOptions();
                    mRtspVideoConn.requestDescribe();
                    mRtspVideoConn.requestSetup("track1", mPortScanner.StartLocalPort());
                    try {
                        sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    mRtspVideoConn.requestPlay();
                    //mRtspVideoConn.requestGetParameter();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void playRTSPAudio(final String ip, final int port, final String func) {
        mAudioDecoderHandler.post(new Runnable() {
            @Override
            public void run() {
                startaudio();

                try {
                    mRtspAudioConn = new RTSPConnector(ip, port, func);
                    mRtspAudioConn.setOnFrameListener(new RTPConnector.OnFrameListener() {
                        @Override
                        public void OnFrame(byte[] buffer) {
                            if (DEBUG_SAVE_AUDIO_FILE) {
                                try {
                                    Log.d(TAG, "writing ...");
                                    mAudioFileOutputStream.write(buffer);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

                            //Log.d(TAG, "dequeueInputBuffer  ing ... " + buffer.length);
                            int indexInputbuffer = mAudioCodec.dequeueInputBuffer(-1);
                            if (indexInputbuffer >= 0) {
                                mAudioInputBuffers[indexInputbuffer].clear();
                                mAudioInputBuffers[indexInputbuffer].put(buffer);

                                mAudioCodec.queueInputBuffer(indexInputbuffer, 0, buffer.length, ++mAudioCount, 0);

                                int indexOutputbuffer;
                                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                                //Log.d(TAG, "dequeueOutputBuffer  ing ... ");
                                while ((indexOutputbuffer = mAudioCodec.dequeueOutputBuffer(info, 0)) >= 0) {
                                    //Log.d(TAG, "DequeueOutputBuffer success : " + indexOutputbuffer);
                                    // Simply ignore codec config buffers.
                                    //if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                    //    Log.i(TAG, "ignore codec config buffer");
                                    //    mAudioCodec.releaseOutputBuffer(indexOutputbuffer, false);
                                    //    continue;
                                    //}

                                    if (info.size != 0) {
                                        ByteBuffer outBuf = mAudioOutputBuffers[indexOutputbuffer];
                                        //Log.d(TAG, "play :"+indexOutputbuffer+ " size:" + info.size + " off:" + info.offset + " flag:"+info.flags);
                                        outBuf.position(info.offset);
                                        outBuf.limit(info.offset + info.size);
                                        final byte[] pcm = new byte[info.size];
                                        outBuf.get(pcm, info.offset, info.size);
                                        outBuf.clear();
                                        mAudioTrack.write(pcm, 0, pcm.length);
                                        //mAudioTrack.play();
                                    }
                                    if (DEBUG_QUEUE_DEQUEUE)
                                        Log.d(TAG, " release : " + indexOutputbuffer);
                                    mAudioCodec.releaseOutputBuffer(indexOutputbuffer, false);

                                }

                                if (indexOutputbuffer < 0) {
                                    switch (indexOutputbuffer) {
                                        case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                                            if (DEBUG_QUEUE_DEQUEUE)
                                                Log.d(TAG, " buffer changed : " + indexOutputbuffer);
                                            mAudioOutputBuffers = mAudioCodec.getOutputBuffers();
                                            break;
                                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                                            if (DEBUG_QUEUE_DEQUEUE)
                                                Log.d(TAG, " format changed : " + indexOutputbuffer);
                                            break;
                                        case MediaCodec.INFO_TRY_AGAIN_LATER:
                                            if (DEBUG_QUEUE_DEQUEUE)
                                                Log.d(TAG, " try again later : " + indexOutputbuffer);
                                            break;
                                        default:
                                            Log.d(TAG, "indexOutputbuffer = " + indexOutputbuffer);
                                    }
                                }
                            }
                        }
                    });
                    mRtspAudioConn.requestOptions();
                    mRtspAudioConn.requestDescribe();
                    mRtspAudioConn.requestSetup("track1", 56740);
                    mRtspAudioConn.requestPlay();
                    //mRtspAudioConn.requestGetParameter();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

//    private void playLocalVideo(String filepath){
//        startvideo();
//
//        try {
//            FileInputStream fin = new FileInputStream(filepath);
//            final int length = fin.available();
//            Log.d(TAG, "prepare to play local file :" + filepath + " ; length:" + length);
//
//            byte[] readbuffer = new byte[length];
//            int ret = 0;
//            fin.read(readbuffer);
//
//            int mark0 = 0;
//            int lastNALindex = 0;
//            int framelengh = 0;
//            for (int i=0; i<length; i++) {
//                if (readbuffer[i] == 0x0) {
//                    mark0++;
//                } else if (readbuffer[i] == 0x1) {
//                    if (mark0 >= 3 && i > 3){
//                        Log.d(TAG, " FIND one frame ! i="+i);
//                        framelengh = i-3 - lastNALindex;
//                        byte[] buffer = new byte[framelengh];
//                        System.arraycopy(readbuffer, lastNALindex, buffer, 0, framelengh);
//                        //DataParser.H264_NAL_Parser(buffer); // TEST
//                        int indexInputbuffer = mVideoCodec.dequeueInputBuffer(-1);
//                        //Log.d(TAG, " > dequeueInputBuffer :" + indexInputbuffer);
//                        if (indexInputbuffer >= 0) {
//                            mVideoInputBuffers[indexInputbuffer].clear();
//                            mVideoInputBuffers[indexInputbuffer].put(buffer);
//
//                            mVideoCodec.queueInputBuffer(indexInputbuffer, 0, framelengh, ++mVideoCount, 0);
//
//                            //Log.d(TAG, "dequeueOutputBuffer  ing ... ");
//                            int indexOutputbuffer = -10;
//                            while( (indexOutputbuffer = mVideoCodec.dequeueOutputBuffer(new MediaCodec.BufferInfo(), 0)) >= 0) {
//                                //Log.d(TAG, "dequeueOutputBuffer : " + indexOutputbuffer);
//                                switch (indexOutputbuffer) {
//                                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED://信息输出缓冲区改变
//                                        Log.d(TAG, " buffer changed : " + indexOutputbuffer);
//                                        break;
//                                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED://信息输出格式改变了
//                                        Log.d(TAG, " format changed : " + indexOutputbuffer);
//                                        break;
//                                    case MediaCodec.INFO_TRY_AGAIN_LATER://超时
//                                        Log.d(TAG, " try again later : " + indexOutputbuffer);
//                                        break;
//                                    default:
//                                        Log.d(TAG, " release : " + indexOutputbuffer);
//                                        mVideoCodec.releaseOutputBuffer(indexOutputbuffer, true);
//                                        break;
//                                }
//                            }
//
//                            Thread.sleep(33);
//                        } else {
//                            Log.e(TAG, "dequeueInputBuffer index error . " + indexInputbuffer);
//                        }
//
//                        lastNALindex = i-3;
//                        mark0 = 0;
//                    } else {
//                        mark0 = 0;
//                    }
//                } else {
//                    mark0 = 0;
//                }
//            }
//        } catch (FileNotFoundException e) {
//            Log.e(TAG, "no such file " + filepath);
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//    }
//
//    private void playLocalAudio(String filepath) {
//        startaudio();
//        long presentTime = 0;
//
//        try {
//            MediaExtractor extractor = new MediaExtractor();
//            extractor.setDataSource(filepath);
//            extractor.selectTrack(0);
//            Log.d(TAG, "count : " + extractor.getTrackCount());
//
//            boolean sawOutputEOS = false;
//            while(!sawOutputEOS) {
//
//                int indexInputbuffer = mAudioCodec.dequeueInputBuffer(-1);
//                if (indexInputbuffer >= 0) {
//                    mAudioInputBuffers[indexInputbuffer].clear();
//                    int inputsize = extractor.readSampleData(mAudioInputBuffers[indexInputbuffer],0);
//                    byte[] wf = new byte[inputsize];
//                    mAudioInputBuffers[indexInputbuffer].get(wf);
//
//                    mAudioCodec.queueInputBuffer(indexInputbuffer, 0, inputsize, ++mAudioCount, 0);
//                    extractor.advance();
//
//                    int indexOutputbuffer;
//                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
//                    Log.d(TAG, "dequeueOutputBuffer  ing ... ");
//                    while ((indexOutputbuffer = mAudioCodec.dequeueOutputBuffer(info, 0)) >= 0) {
//                        Log.d(TAG, "DequeueOutputBuffer success : " + indexOutputbuffer);
//
//                        switch (indexOutputbuffer) {
//                            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
//                                if (DEBUG_QUEUE_DEQUEUE)
//                                    Log.d(TAG, " buffer changed : " + indexOutputbuffer);
//                                mAudioOutputBuffers = mAudioCodec.getOutputBuffers();
//                                break;
//                            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
//                                if (DEBUG_QUEUE_DEQUEUE)
//                                    Log.d(TAG, " format changed : " + indexOutputbuffer);
//                                break;
//                            case MediaCodec.INFO_TRY_AGAIN_LATER:
//                                if (DEBUG_QUEUE_DEQUEUE)
//                                    Log.d(TAG, " try again later : " + indexOutputbuffer);
//                                break;
//                            default:
//                                if (info.size != 0) {
//                                    ByteBuffer outBuf = mAudioOutputBuffers[indexOutputbuffer];
//                                    Log.d(TAG, "play :"+indexOutputbuffer+ " size:" + info.size + " off:" + info.offset + " flag:"+info.flags);
//                                    outBuf.position(info.offset);
//                                    outBuf.limit(info.offset + info.size);
//                                    byte[] data = new byte[info.size];
//                                    outBuf.get(data);
//                                    Log.d(TAG, " -- outbuf : "+ data.length);
//                                    mAudioTrack.write(data, 0, data.length);
//                                }
//                                if (DEBUG_QUEUE_DEQUEUE)
//                                    Log.d(TAG, " release : " + indexOutputbuffer);
//                                mAudioCodec.releaseOutputBuffer(indexOutputbuffer, false);
//                                break;
//                        }
//                    }
//                }
//                //Thread.sleep(500);
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

    public void pause() {

        new Thread() {
            @Override
            public void run() {
                try {
                    if (mRtspVideoConn != null) {
                        mRtspVideoConn.requestTeardown("track1");
                        mRtspVideoConn.disconnect();
                    }
                    if (mRtspAudioConn != null) {
                        mRtspAudioConn.requestTeardown("track1");
                        mRtspAudioConn.disconnect();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mVideoCodec.stop();
                mAudioTrack.stop();
                mAudioCodec.stop();
            }
        }.start();
    }


    public void stop() {

        new Thread() {
            @Override
            public void run() {
                try {
                    if (mRtspVideoConn != null) {
                        mRtspVideoConn.requestTeardown("track1");
                        mRtspVideoConn.disconnect();
                    }
                    if (mRtspAudioConn != null) {
                        mRtspAudioConn.requestTeardown("track1");
                        mRtspAudioConn.disconnect();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
//                mVideoCodec.stop();
//                mSurface = null;
//                mVideoCodec.configure(null, mSurface, null, 0);
////                mVideoCodec.reset();
//                mVideoCodec.release();
//                mAudioTrack.stop();
//                mAudioCodec.stop();
////                mAudioCodec.reset();
//                mAudioCodec.release();
            }
        }.start();
    }

    public String getfps() {
        return "fps: " + mFps + "  (" + mVideoWidth + "x" + mVideoHeight + ")\n";
    }

    public String dump(final int interval) {
        String dbg = "fps: " + mFps + "  (" + mVideoWidth + "x" + mVideoHeight + ")\n";

        if (mRtspVideoConn != null) {
            dbg += "bps: " + mRtspVideoConn.getH264Bps() / interval + " byte/s\n";
            int[] cnt = mRtspVideoConn.getH264NaluTypeCount();
            //long tsvideo = mRtspVideoConn.getLastestTimestamp();
            dbg += "sei/sps/pps/ni/i: " + cnt[6] / interval + "/" + cnt[7] / interval + "/" + cnt[8] / interval + "/" + cnt[1] / interval + "/" + cnt[5] / interval + "\n";
            dbg += "video lost: " + (mRtspVideoConn.getLostPackageCount() / interval) + "/" + (mRtspVideoConn.getRecvPackageCount() / interval) + "\n";
            mRtspVideoConn.resetPackageCount();
        }
        if (mRtspAudioConn != null) {
            //long tsaudio = mRtspAudioConn.getLastestTimestamp();
            dbg += "audio lost: " + (mRtspAudioConn.getLostPackageCount() / interval) + "/" + (mRtspAudioConn.getRecvPackageCount() / interval) + "\n";
            mRtspAudioConn.resetPackageCount();
        }
        if (mRtspVideoConn != null) {
            String vdbg = "max f-f interval: " + mRtspVideoConn.getH264FrameInterval() + "ms \n";
            vdbg += "max f delay: " + mRtspVideoConn.getH264FrameDelay() + "ms \n";
            dbg += vdbg;
        }
        return dbg;
    }
}
