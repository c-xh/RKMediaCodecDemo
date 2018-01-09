package com.rockchip.rkmediacodecdemo.rtsp;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by zhanghao on 2016/12/28.
 */

public class RTSPConnector {
    private static final String TAG = "RTSPConnector";
    private Socket mSocket = null;
    private InputStream mInputStream = null;
    private OutputStream mOutputStream = null;
    private int mCSeq = 1;
    private String mIP = null;
    private int mPort = 554;
    private String mFunc = null;
    private boolean mIsTCP = false;

    private HandlerThread mResponseThread = null;
    private Handler mResponseHandler = null;

    private RTPConnector mRTPConnect = null;
    private HashMap<String,String> mHeaders = null;
    private String mSession = null;
    private boolean mHasAudioTrack = false;
    private boolean mHasVideoTrack = false;
    private boolean mDidSessionSet = false;
    private boolean mDidMediaScriptSet = false;

    private int mRTPClientPort = 0;
    private int mRTPServerPort = 0;


    private RTPConnector.OnFrameListener mOnFrameCallback = null;

    private  final Pattern regexStatus = Pattern.compile("RTSP/\\d.\\d (\\d+) .+", Pattern.CASE_INSENSITIVE);
    private  final Pattern regexHeader = Pattern.compile("(\\S+): (.+)", Pattern.CASE_INSENSITIVE);
    private  final Pattern regexUDPTransport = Pattern.compile("client_port=(\\d+)-\\d+;server_port=(\\d+)-\\d+", Pattern.CASE_INSENSITIVE);
    private  final Pattern regexTCPTransport = Pattern.compile("client_port=(\\d+)-\\d+;", Pattern.CASE_INSENSITIVE);
    private  final Pattern regexSessionWithTimeout = Pattern.compile("(\\S+);timeout=(\\d+)", Pattern.CASE_INSENSITIVE);
//    private  final Pattern regexSDPgetTrack1 = Pattern.compile("trackID=(\\d+)",Pattern.CASE_INSENSITIVE);
//    private  final Pattern regexSDPgetTrack2 = Pattern.compile("control:(\\S+)",Pattern.CASE_INSENSITIVE);
    private  final Pattern regexSDP_MediadeScript = Pattern.compile("m=(\\S+) .+", Pattern.CASE_INSENSITIVE);
    private  final Pattern regexSDP_PacketizationMode = Pattern.compile("packetization-mode=(\\d);", Pattern.CASE_INSENSITIVE);
    private  final Pattern regexSDP_SPS_PPS = Pattern.compile("sprop-parameter-sets=(\\S+),(\\S+)", Pattern.CASE_INSENSITIVE);
    private  final Pattern regexSDP_Length = Pattern.compile("Content-length: (\\d+)", Pattern.CASE_INSENSITIVE);
//    private static final Pattern regexSDPstartFlag = Pattern.compile("v=(\\d)",Pattern.CASE_INSENSITIVE);

    public RTSPConnector(String IP, int port, String func) throws IOException {
        mIP = IP;
        mPort= port;
        mFunc = func;

        mSocket = new Socket(IP, port);
        mInputStream = mSocket.getInputStream();
        mOutputStream = mSocket.getOutputStream();

        mResponseThread = new HandlerThread(TAG+"Rep");
        mResponseThread.start();
        mResponseHandler = new Handler(mResponseThread.getLooper());

        mHeaders = new HashMap<>();
    }

    public byte[] recvMsg(InputStream inpustream) {
        try {
            byte len[] = new byte[2048];
            int count = inpustream.read(len);
            if(count<=0)
                return null;
            byte[] temp = new byte[count];
            for (int i = 0; i < count; i++) {
                temp[i] = len[i];
            }
            return temp;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    private String getHeaders() {
        return "CSeq: " + (++mCSeq) + "\r\n"
                + "User-Agent: RockchipRtspClient("+"1.0"+") \r\n"
                + ((mSession == null)?"":("Session: " + mSession + "\r\n"));
                //+ "\r\n";
    }

    public void requestOptions() throws IOException {
        String request = "OPTIONS rtsp://" + mIP + ":" + mPort + "/" + mFunc + " RTSP/1.0\r\n" + getHeaders() + "\r\n";
        Log.d(TAG, ">> " + request);
        mOutputStream.write(request.getBytes("UTF-8"));
        mOutputStream.flush();
        parseResponse();


    }

    public void requestDescribe() throws IOException {
        String request = "DESCRIBE rtsp://" + mIP + ":" + mPort + "/" + mFunc + " RTSP/1.0\r\n" + getHeaders()
                + "Accept: application/sdp\r\n" + "\r\n";
        Log.d(TAG, ">> " + request);
        mOutputStream.write(request.getBytes("UTF-8"));
        mOutputStream.flush();
        parseResponse();
    }

    public void requestSetup(String track, int clientport) throws IOException {
        Matcher matcher;
        String request = "SETUP rtsp://" + mIP + "/" + mFunc + "/" + track +" RTSP/1.0\r\n"
                + getHeaders()
                + "Transport: RTP/AVP/"+ (mIsTCP?"TCP":"UDP") + ";unicast;client_port="+clientport+"-"+(clientport+1) + "\r\n"
                + "\r\n";
        Log.d(TAG, ">> " + request);
        mOutputStream.write(request.getBytes("UTF-8"));
        mOutputStream.flush();

        //wait(mDidSessionSet,"mDidSessionSet");
        while(!mDidSessionSet){
            try {
                parseResponse();
                Thread.sleep(10);
                //Log.d(TAG, "wait mDidSessionSet:" + mDidSessionSet);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        matcher = regexSessionWithTimeout.matcher(mHeaders.get("session"));
        if(matcher.find())  {
            mSession = matcher.group(1);
        }
        else {
            mSession = mHeaders.get("session");
        }
        Log.d(TAG, "the session is " + mSession);

        Log.d(TAG, "requestSetup: mHeaders" + mHeaders.toString());
        try {
            if(mIsTCP) matcher = regexTCPTransport.matcher(mHeaders.get("transport"));
            else matcher = regexUDPTransport.matcher(mHeaders.get("transport"));
        }catch (NullPointerException e){
            e.printStackTrace();
        }
        if(matcher.find()) {
            Log.d(TAG, "The client port is:" + matcher.group(1) + " ,the server prot is:" + (mIsTCP?"null":matcher.group(2)) + "...");
            mRTPClientPort = Integer.parseInt(matcher.group(1));
            if(!mIsTCP) mRTPServerPort = Integer.parseInt(matcher.group(2));
            //prepare for the decoder
            //wait(mDidMediaScriptSet,"mDidMediaScriptSet");
            while(!mDidMediaScriptSet){
                try {
                    Thread.sleep(10);
                    //Log.d(TAG, "wait mDidMediaScriptSet:" + mDidMediaScriptSet);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            RTPConnector.RTPType rtpType = null;
            Log.d(TAG, "requestSetup: mHasVideoTrack = "+mHasVideoTrack);
            Log.d(TAG, "requestSetup: mHasAudioTrack = "+mHasAudioTrack);
            if (mHasVideoTrack ) rtpType = RTPConnector.RTPType.VIDEO;
            else if(mHasAudioTrack ) rtpType = RTPConnector.RTPType.AUDIO;
            else Log.e(TAG, "unsupport multi track !");
            Log.d(TAG, "Create Socket from client :"+mRTPClientPort+" to server:"+mIP+":"+mRTPServerPort);
            mRTPConnect = new RTPConnector(mIsTCP, mRTPClientPort, mIP, mRTPServerPort, rtpType);
            mRTPConnect.setOnFrameListener(mOnFrameCallback);
            mRTPConnect.connect();
        } else {
            Log.e(TAG, "transport setting no found !");
        }
    }

    public void requestPlay() throws IOException {
        String request = "PLAY rtsp://" + mIP + ":" + mPort + "/" + mFunc + "/ RTSP/1.0\r\n"
                + getHeaders()
                + "Range: npt=0.000-\r\n"
                + "\r\n";
        Log.d(TAG, ">> " + request);
        mOutputStream.write(request.getBytes("UTF-8"));
        mOutputStream.flush();
        parseResponse();
    }

    public void requestGetParameter() throws IOException {
        String request = "GET_PARAMETER rtsp://" + mIP + ":" + mPort + "/" + mFunc + "/ RTSP/1.0\r\n"
                + getHeaders() + "\r\n";
        Log.d(TAG, ">> " + request);
        mOutputStream.write(request.getBytes("UTF-8"));
        mOutputStream.flush();
        parseResponse();
    }

    public void requestTeardown(String track) throws IOException {
        String request = "TEARDOWN rtsp://" + mIP + "/" + mFunc + "/" + track + " RTSP/1.0\r\n" + getHeaders() + "\r\n";
        Log.d(TAG, ">> " + request);
        mOutputStream.write(request.getBytes("UTF-8"));
        mOutputStream.flush();
    }

    public void parseResponse(/*InputStream input*/) throws IOException {
//        BufferedReader bufferReader = new BufferedReader(new InputStreamReader(input));

        String line;
        Matcher matcher;
        int state = -1;

        int sdpContentLength = 0;
        int packetizationMode = 0;
        String SPS = "";
        String PPS = "";

        // 接受服务器的信息
//        while (true) {
            byte[] by = recvMsg(mInputStream);
            if (by == null) {
                return;
            }

            try {
                line = new String(by);

                String[] sArray = line.split("\r\n");
                for (int i = 0; i < sArray.length; i++) {

                    Log.d(TAG, "-----------------------i" + i + ":" + sArray[i].toString());
//        while ( (file_dialog_item = bufferReader.readLine()) != null) {
                    Log.d(TAG, "<< " + sArray[i]);

            /* GET STATUS */
                    matcher = regexStatus.matcher(sArray[i]);
                    if (matcher.find()) {
                        state = Integer.parseInt(matcher.group(1));
                        // Log.d(TAG, "++ [STATUS = "+state+"]");
                    }

            /* GET HEADER */
                    matcher = regexHeader.matcher(sArray[i]);
                    if (matcher.find()) {
                        String key = matcher.group(1).toLowerCase(Locale.US);
                        String value = matcher.group(2);
                        mHeaders.put(key, value);
                        // Log.d(TAG, "++ [HEADER] "+ key + " : " +  value);

                        if (key.equals("session")) {
                            mDidSessionSet = true;
                            Log.d(TAG, "session set !" + mDidSessionSet);
                        }
                    }

            /* GET SDP length */
                    matcher = regexSDP_Length.matcher(sArray[i]);
                    if (matcher.find()) {
                        sdpContentLength = Integer.parseInt(matcher.group(1));
                        // Log.d(TAG, "++ [SDP LENGTH]");
                    }

            /* GET SDP MediaScript */
                    matcher = regexSDP_MediadeScript.matcher(sArray[i]);
                    if (matcher.find()) {
                        if (matcher.group(1).equalsIgnoreCase("audio")) {
                            mHasAudioTrack = true;
                            mDidMediaScriptSet = true;
                        } else if (matcher.group(1).equalsIgnoreCase("video")) {
                            mHasVideoTrack = true;
                            mDidMediaScriptSet = true;
                        }
                        // Log.d(TAG, "++ [SDP MEDIASCRIPT]" + matcher.group(1));
                    }

                    mHasVideoTrack = true;
            /* GET SDP Packetization Mode */
                    matcher = regexSDP_PacketizationMode.matcher(sArray[i]);
                    if (matcher.find()) {
                        packetizationMode = Integer.parseInt(matcher.group(1));
                        // Log.d(TAG, "++ [SDP PacketizationMode]" + packetizationMode);
                    }

            /* GET SDP SPS PPS */
                    matcher = regexSDP_SPS_PPS.matcher(sArray[i]);
                    if (matcher.find()) {
                        SPS = matcher.group(1);
                        PPS = matcher.group(2);
                        // Log.d(TAG, "++ [SDP SPS PPS]");
                    }
                }
//            break;
        }catch (Exception e){
            e.printStackTrace();
            Log.d(TAG, "run Thread::解析Socket数据异常！！！！！！！！！！！！！！！"  );
        }

//        }

        Log.w(TAG, "== Connection lost");
    }

    public void disconnect() {
        if(mRTPConnect != null)
            mRTPConnect.disconnect();
    }

    /*private void wait(Boolean sth, String debug){
        while(!sth){
            try {
                Thread.sleep(1000);
                Log.d(TAG, "wait .." + debug + ":" + sth);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }*/

    public void setOnFrameListener(RTPConnector.OnFrameListener listener) {
        mOnFrameCallback = listener;
    }

    public int getLostPackageCount(){
        if (mRTPConnect != null)
            return mRTPConnect.getLostPackageCount();
        else
            return 0;
    }
    public int getRecvPackageCount(){
        if (mRTPConnect != null)
            return mRTPConnect.getRecvPackageCount();
        else
            return 0;
    }
    public long getLastestTimestamp(){
        if (mRTPConnect != null)
            return mRTPConnect.getLastestSequeceTime();
        else
            return 0;
    }
    public void resetPackageCount(){
        if (mRTPConnect != null)
            mRTPConnect.resetPackageCount();
    }

    public long getH264FrameDelay(){
        if (mRTPConnect != null)
            return mRTPConnect.getH264FrameDelay();
        else
            return 0;
    }
    public long getH264FrameInterval(){
        if (mRTPConnect != null)
            return mRTPConnect.getH264FrameInterval();
        else
            return 0;
    }
    public long getH264Bps() {
        if (mRTPConnect != null)
            return mRTPConnect.getH264Bps();
        else
            return 0;
    }
    public int[] getH264NaluTypeCount(){
        if (mRTPConnect != null)
            return mRTPConnect.getH264NaluTypeCount();
        else
            return new int[10];
    }
}
