package com.rockchip.rkmediacodecdemo.rtsp;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Created by zhanghao on 2016/12/29.
 */

public class RTPConnector {
    private static final String TAG = "RTPConnector";
    private static final boolean DEBUG_NALU = false;

    private RTPType mRTPType = null;
    private RTPThread mRTPThread = null;
    private RTCPThread mRTCPThread = null;

    private boolean mIsTCP = false;
    private String mServerIP = "";
    private int mClientPort = 0;
    private int mServerPort = 0;
    private byte[] message = new byte[2048];

    private long mLastSequenceNum = -1;
    private int mServerSSRC = -1;
    private long mLastSR = 0;
    private long mLastTimestamp = 0;
    private long mLast264Timestamp = 0;
    private long mFirstPackageTimestamp = 0;
    private long mH264FrameDelay = 0;
    private long mH264FrameInterval = 0;

    private int mLostPackageCount = 0;
    private int mRecvPackageCount = 0;
    private long mLastestSequenceTime = 0;
    private long mBps = 0;
    private int[] mNaluTypeCount = {0,0,0,0,0,0,0,0,0,0};

    private OnFrameListener mOnFrameCallback = null;

    public enum RTPType { VIDEO, AUDIO }

    public RTPConnector(boolean isTCP, int clientPort, String serverIP, int serverPort, RTPType rtpType) {
        mIsTCP = isTCP;
        mClientPort = clientPort;
        mServerIP = serverIP;
        mServerPort = serverPort;
        mRTPType = rtpType;
    }

    public void connect() {

        if(mIsTCP) {
            Log.e(TAG, "unsupport tcp yet !");
        } else {
            mRTPThread = new RTPThread(mClientPort, mServerIP, mServerPort);
            mRTCPThread = new RTCPThread(mClientPort+1, mServerIP, mServerPort+1);
            mRTPThread.start();
            mRTCPThread.start();
        }
    }

    public void disconnect() {
        if (mRTPThread == null || mRTCPThread == null)
            return; // Already Disconnected
        Log.w(TAG, "RTP Connector disconnecting ...");
        mRTCPThread.sendReciveReport(RTCPPackage.RTCP_PAYLOAD_TYPE_BYE);
        try {
            mRTPThread.join();
            mRTCPThread.join();
            mRTPThread = null;
            mRTCPThread = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public int getLostPackageCount() {
        return mLostPackageCount;
    }
    public int getRecvPackageCount() {return mRecvPackageCount;}
    public long getLastestSequeceTime() {return mLastestSequenceTime;}
    public long getH264FrameDelay() {long r = mH264FrameDelay;mH264FrameDelay = 0;return r;}
    public long getH264FrameInterval() {long r = mH264FrameInterval;mH264FrameInterval = 0;return r;}
    public long getH264Bps() {long r=mBps;mBps=0;return r;}
    public int[] getH264NaluTypeCount() {int[] r=mNaluTypeCount;mNaluTypeCount= new int[10];return r;}
    public void resetPackageCount() {mLostPackageCount = 0;mRecvPackageCount = 0;}

    private class RTPThread extends Thread {
        private int mLocalPort = 0;
        private int mRemotePort = 0;
        private String mSerIP  = "";
        private volatile boolean mIsStop = false;
        private long recordTime = 0;
        private boolean mDbgIsComplete = false;

        private DatagramSocket mSocket;
        private DatagramPacket mPacket;


        public RTPThread(int localPort, String serverip, int serverPort){
            mLocalPort = localPort;
            mRemotePort = serverPort;
            mSerIP = serverip;

            try {
                mSocket = new DatagramSocket(mClientPort);
                mPacket = new DatagramPacket(message,message.length);
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }

        public void exit(){
            Log.w(TAG, "RTP thread exiting ...");
            mIsStop = true;
        }

        public void disconnect() {
            mSocket.disconnect();
            mSocket.close();
        }

        @Override
        public void run() {
            RTPPackage rtppkg =new RTPPackage().create(mPacket.getData(), mPacket.getLength());
//            int size = 170*1024;
            while(!mIsStop) {
                long currentTime;
                try {
                    if (mSocket.isClosed())
                        return;
                    mSocket.receive(mPacket);
//                    if(DefaultConfig.DEFAULT_DEBUG_DELAY) Log.d(DefaultConfig.TAG, "1. recv a package: size:" + mPacket.getLength() + " : " + System.currentTimeMillis());
                    rtppkg =rtppkg.create(mPacket.getData(), mPacket.getLength());
                    ++mRecvPackageCount;
                    mBps += mPacket.getLength();
                    mLastestSequenceTime = rtppkg.timestamp;
                    if (mServerSSRC != rtppkg.ssrc)
                        mServerSSRC = rtppkg.ssrc;
                    if(DEBUG_NALU) Log.d(TAG, "got rtp len:" + mPacket.getLength() + ";" + rtppkg.toString());
                    if (rtppkg.sequenceNumber != mLastSequenceNum + 1 && mLastSequenceNum != -1) {
                        Log.w(TAG, "lost RTP package !! last:" + mLastSequenceNum + " ; recv:"+rtppkg.sequenceNumber);
//                        if(DefaultConfig.DEFAULT_DEBUG_DELAY) Log.e(DefaultConfig.TAG, "x. lost RTP package !! last:" + mLastSequenceNum + " ; recv:"+rtppkg.sequenceNumber);
                        mLostPackageCount += (rtppkg.sequenceNumber-mLastSequenceNum-1);
                    }
                    mLastSequenceNum = rtppkg.sequenceNumber;
                    //有些负载类型由于诞生的较晚，没有具体的PT值，只能使用动态（dynamic）PT值，即96到127，这就是为什么大家普遍指定H264的PT值为96。
                    if (rtppkg.ptype >= RTPPackage.PTYPE_DYNAMIC_MIN && rtppkg.ptype <= RTPPackage.PTYPE_DYNAMIC_MAX) {
//                        Log.d(TAG, "run: mRTPType = " +mRTPType);
                        if(mRTPType!=null)
                        switch (mRTPType) {
                            case VIDEO:
                                recoverH264Package(rtppkg);
                                break;
                            case AUDIO:
                                recoverAACPackage(rtppkg);
                                break;
                        }
                    } else {
                        Log.w(TAG, "got a unknowable RTP package, type=" + rtppkg.ptype);
                    }

                    //RTCP START : every 10s send a rtcp packet to server
                    currentTime = System.currentTimeMillis();
                    if(currentTime - recordTime > 10000) {
                        recordTime = currentTime;
                        mRTCPThread.sendReciveReport(RTCPPackage.RTCP_PAYLOAD_TYPE_RR);
                    }
                    // RTCP END
                } catch (IOException e) {
                    //e.printStackTrace();
                    Log.e(TAG, "RTP Socket maybe exited !");
                }
            }
            Log.d(TAG, "RTPThread over !");
        }

        H264Package mH264Package = new H264Package();
        NALUnit mNALUnit = new NALUnit();
        private long test = 8;
        private void recoverH264Package(RTPPackage rtp) throws IOException {
            if(mDbgIsComplete) {
                mFirstPackageTimestamp = System.currentTimeMillis();
                mDbgIsComplete = false;
            }
            NALUnit nalu = mNALUnit.getCompleteNALUnit(rtp);
//            NALUnit tmpHeader = NALUnit.parseHeader(rtp); //没用到？
            /*Log.d(DefaultConfig.TAG, ">. get one NALU. type="+tmpHeader.nal_unit_type+";mb="+tmpHeader.first_mb_in_slice
                    +";fu_type="+tmpHeader.fua_nal_unit_type+";fu_mb="+tmpHeader.fua_first_mb_in_slice
                    +";fu_start?"+tmpHeader.fu_isStart+";fu_end?"+tmpHeader.fu_isEnd);*/

            if (nalu != null) {
                if(DEBUG_NALU) Log.d(TAG, "** get complete NALU frame ! **");
                H264Package h264frame = null;
                mH264Package.sendNALUnit(nalu);
                while((h264frame = mH264Package.getCompleteH264()) != null) {
                    if (h264frame != null) {
                        long currentTime = System.currentTimeMillis();
                        mH264FrameInterval = Math.max(currentTime - mLast264Timestamp, mH264FrameInterval);
                        mLast264Timestamp = currentTime;
                        mH264FrameDelay = Math.max(currentTime - mFirstPackageTimestamp, mH264FrameDelay);
                        mDbgIsComplete = true;
                        mNaluTypeCount[h264frame.mNALU.get(0).nal_unit_type] += 1;
                        //Log.d(TAG, "** get complete h264 frame ! **");
                        if (mOnFrameCallback != null) {
//                            if (DefaultConfig.DEFAULT_DEBUG_DELAY)
//                                Log.e(DefaultConfig.TAG, "2. get a complete h264 frame:[" + h264frame.mNALU.get(0).nal_unit_type + "]: " + System.currentTimeMillis());
                            mOnFrameCallback.OnFrame(h264frame.getBytes());
                            //else Log.e(DefaultConfig.TAG, "*. skip");
                        }
                    } else {
                        //Log.d(TAG, "** combining h264 frame ... **");
                    }
                }
            } else {
                //Log.d(TAG, "** combining NALU frame ... **");
            }
        }

        AACPackage aac =new AACPackage();
        private void recoverAACPackage(RTPPackage rtp) throws IOException {
            aac = aac.getCompleteAACPackage(rtp);
            if (mOnFrameCallback != null)
                mOnFrameCallback.OnFrame(new AACPackage().getCompleteAACPackage(rtp).data);
        }
    }

    private class RTCPThread extends Thread {
        private HandlerThread mSenderThread;
        private Handler mSenderHandler;
        private DatagramSocket mSocket;
        private DatagramPacket mPacket;
        private volatile boolean mIsStop = false;

        private int mLocalPort = 0;
        private int mRemotePort = 0;
        private String mSerIP  = "";

        public RTCPThread(int localPort, String serverip, int serverPort) {
            mLocalPort = localPort;
            mRemotePort = serverPort;
            mSerIP = serverip;

            mSenderThread = new HandlerThread("RTCHSender");
            mSenderThread.start();
            mSenderHandler = new Handler(mSenderThread.getLooper());
            try {
                mSocket = new DatagramSocket(mLocalPort);
            } catch (SocketException e) {
                e.printStackTrace();
            }
            mPacket = new DatagramPacket(message,message.length);
        }

        public void exit(){
            Log.w(TAG, "RTCP thread exiting ...");
            mIsStop = true;
        }

        public void disconnect() {
            mSocket.disconnect();
            mSocket.close();
        }
        RTCPPackage mRTCPPackage = new RTCPPackage();
        @Override
        public void run() {
            while(!mIsStop) {
                try {
                    if (mSocket.isClosed())
                        return;
                    mSocket.receive(mPacket);
                    RTCPPackage sr = mRTCPPackage.parseSR(mPacket.getData());
                    //byte[] sr = mPacket.getData();
                    mLastSR = sr.mNtpTimestamp;
                    mLastTimestamp = (short) System.currentTimeMillis();
                    Log.d(TAG, "RTCP recieve a SR package("+mPacket.getData().length+"): ntp="+sr.mNtpTimestamp);
                } catch (IOException e) {
                    //e.printStackTrace();
                    Log.e(TAG, "RTCP Socket maybe exited !");
                }
            }
            Log.d(TAG, "RTCPThread over !");
        }

        public void sendReciveReport(final byte ptype) {
            mSenderHandler.post(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "Rtcp send report :"+ptype);
                    sendReport(ptype);
                }
            });
        }

        private void sendReport(final byte ptype) {
            if (mServerSSRC == -1)
                return;

            long dlsr = (System.currentTimeMillis() - mLastTimestamp)/65536000;
            RTCPPackage packet = mRTCPPackage.create(ptype, mServerSSRC, (int)mLastSequenceNum, mLastSR, dlsr);
            try {
                if (ptype == RTCPPackage.RTCP_PAYLOAD_TYPE_RR) {
                    byte[] rtcpRR = packet.getRRBytes();
                    Log.d(TAG, "send RR, len:" + packet.getRRBytes().length + "| ssrc:" + mServerSSRC);
                    mPacket = new DatagramPacket(rtcpRR, rtcpRR.length, InetAddress.getByName(mSerIP), mRemotePort);
                } else if (ptype == RTCPPackage.RTCP_PAYLOAD_TYPE_BYE) {
                    byte[] rtcpBYE = packet.getBYEBytes();
                    Log.d(TAG, "send BYE, len:" + packet.getBYEBytes().length + "| ssrc:" + mServerSSRC);
                    mPacket = new DatagramPacket(rtcpBYE, rtcpBYE.length, InetAddress.getByName(mSerIP), mRemotePort);
                }

                mSocket.send(mPacket);

                if (ptype == RTCPPackage.RTCP_PAYLOAD_TYPE_BYE) {
                    mRTCPThread.exit();
                    mRTPThread.exit();
                    mRTCPThread.disconnect();
                    mRTPThread.disconnect();
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public interface OnFrameListener {
        void OnFrame(byte[] buffer);
    }
    public void setOnFrameListener(OnFrameListener listener) {
        mOnFrameCallback = listener;
    }
}
