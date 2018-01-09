package com.rockchip.rkmediacodecdemo.rtsp;

import java.util.Random;

/**
 * Created by zhanghao on 2017/1/3.
 */

public class RTCPPackage {
    private static final String TAG = "RTCPPackage";

    public static final byte RTCP_PAYLOAD_TYPE_SR   = (byte)200;    // Sender Report
    public static final byte RTCP_PAYLOAD_TYPE_RR   = (byte)201;    // Receiver Report
    public static final byte RTCP_PAYLOAD_TYPE_SDES = (byte)202;    // Source Description
    public static final byte RTCP_PAYLOAD_TYPE_BYE  = (byte)203;    // Say Goodbye

    private byte mVP = (byte)0x80; // & 0xE0; // 1110 0000
    private byte mRC = (byte)0x01; // & 0x1F; // 0001 1111
    private byte mPT = 0;
    private byte mLength = 0;
    private int mHighestSequenceNum = 0;
    public long mLastSR = 0;
    public long mDelayLastSR = 0;
    public long mNtpTimestamp = 0;

    private  int mSSRC0 = -1; // sender ssrc
    private  int mSSRC1 = 0;

    public  RTCPPackage create(byte pt, int ssrc, int lastsn, long lsr, long dlsr) {
        mSSRC1 = ssrc;
        RTCPPackage packet = new RTCPPackage();
        packet.mPT = pt;
        packet.mHighestSequenceNum = lastsn;
        packet.mLastSR = lsr;
        packet.mDelayLastSR = dlsr;

        if (mSSRC0 == -1) {
            Random rnd = new Random(System.currentTimeMillis());
            mSSRC0 = rnd.nextInt();
        }

        return packet;
    }

    public  RTCPPackage parseSR(byte[] sr){
        RTCPPackage packet = new RTCPPackage();
        if(sr.length >= 14)
            packet.mNtpTimestamp = ((sr[10]&0xFF)<<24) | ((sr[11]&0xFF)<<16) | ((sr[12]&0xFF)<<8) | (sr[13]&0xFF);
        //if(sr.length >= 48)
        //    packet.mLastSR = sr[44]<<24 | sr[45]<<16 | sr[46]<<8 | sr[47];
        //if(sr.length >= 52)
        //    packet.mDelayLastSR = sr[48]<<24 | sr[49]<<16 | sr[50]<<8 | sr[51];
        return packet;
    }

    /* Sendor Report
       0               1               2               3
       0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |V=2|P|    RC   |      PT       |            length             | 3
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                        SSRC of sender                         | 7
      +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
      |             NTP timestamp, most significant word              | 11
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |             NTP timestamp, least significant word             | 15
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                     RTP  timestampe                           | 19
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                   sender's packet count                       | 23
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                   sender's octet count                        | 27
      +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
      |                           SSRC_1                              | 31
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      | fraction lost |     cumulative number of packets lost         | 35
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |         extended highest sequence number received             | 39
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                     interarrival jitter                       | 43
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                     last SR  (LSR)                            | 47
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                 delay since last SR (DLSR)                    | 51
      +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
   */

    /* Receiver Report
       0               1               2               3
       0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |V=2|P|    RC   |      PT       |            length             | 3
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                        SSRC of sender                         | 7
      +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
      |                           SSRC_1                              | 11
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      | fraction lost |     cumulative number of packets lost         | 15
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |         extended highest sequence number received             | 19
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                     interarrival jitter                       | 23
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                     last SR  (LSR)                            | 27
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                 delay since last SR (DLSR)                    | 31
      +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
   */

    /* Source Description
       0               1               2               3
       0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |V=2|P|    RC   |      PT       |            length             | 3
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                        SSRC / CSRC_1                          | 7
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                        SDES items                             | 11
      |                             ...                               |
      +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
      |                             ...                               |
      +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+

      Items:
       0               1               2               3
       0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |  ITEM         |    length     |       content ...
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

         CNAME=1
         NAME=2
         EMAIL=3
         PHONE=4
         LOC=5      // Location
         TOOL=6     // Client Tool
         NOTE=7
         PRIV=8
    */

     /* BYE
       0               1               2               3
       0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |V=2|P|    RC   |      PT       |            length             | 3
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                        SSRC / CSRC_1                          | 7
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                             ...                               |
      +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
      |     length    |         reason for leaving                    |
      +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
     */


    public byte[] getRRBytes() {
        // Head :
        //  SR/RR = 8byte
        //  SDES = 4byte
        byte[] bytes = new byte[32];
        bytes[0] = (byte)((mVP&0xE0)|(mRC&0x1F));
        bytes[1] = mPT;
        bytes[2] = 0;                        // Length H16
        bytes[3] = (byte)(bytes.length/4-1); // Length L16  (32bit=1)

        // SSRC
        bytes[4] = (byte)((mSSRC0&0xFF000000) >> 24);
        bytes[5] = (byte)((mSSRC0&0x00FF0000) >> 16);
        bytes[6] = (byte)((mSSRC0&0x0000FF00) >> 8);
        bytes[7] = (byte)((mSSRC0&0x000000FF));

        // SSRC 1
        bytes[8] = (byte)((mSSRC1&0xFF000000) >> 24);
        bytes[9] = (byte)((mSSRC1&0x00FF0000) >> 16);
        bytes[10] = (byte)((mSSRC1&0x0000FF00) >> 8);
        bytes[11] = (byte)((mSSRC1&0x000000FF));

        // fraction lost
        bytes[12] = 0;
        // cumulative number of packets lost
        bytes[13] = (byte)0xFF;
        bytes[14] = (byte)0xFF;
        bytes[15] = (byte)0xFF;

        // extended highest sequence number received
        bytes[16] = (byte)((mHighestSequenceNum&0xFF000000) >> 24);
        bytes[17] = (byte)((mHighestSequenceNum&0x00FF0000) >> 16);
        bytes[18] = (byte)((mHighestSequenceNum&0x0000FF00) >> 8);
        bytes[19] = (byte)((mHighestSequenceNum&0x000000FF));

        // interarrival jitter // TODO::
        bytes[20] = 0x0;
        bytes[21] = 0x0;
        bytes[22] = 0x0;
        bytes[23] = 0x3E;

        // last SR
        bytes[24] = (byte)((mLastSR&0xFF000000) >> 24);
        bytes[25] = (byte)((mLastSR&0x00FF0000) >> 16);
        bytes[26] = (byte)((mLastSR&0x0000FF00) >> 8);
        bytes[27] = (byte)((mLastSR&0x000000FF));

        // delay since last SR
        bytes[28] = (byte)((mDelayLastSR&0xFF000000) >> 24);
        bytes[29] = (byte)((mDelayLastSR&0x00FF0000) >> 16);
        bytes[30] = (byte)((mDelayLastSR&0x0000FF00) >> 8);
        bytes[31] = (byte)((mDelayLastSR&0x000000FF));
        return bytes;
    }

    public byte[] getSDESBytes() {
        // TODO::
        byte[] bytes = new byte[32];
        bytes[0] = (byte)0x80;
        bytes[1] = RTCP_PAYLOAD_TYPE_SDES;
        bytes[2] = 0;                        // Length H16
        bytes[3] = (byte)(bytes.length/4-1); // Length L16  (32bit=1)
        return bytes;
    }

    public byte[] getBYEBytes() {
        byte[] bytes = new byte[8];
        bytes[0] = (byte)((mVP&0xE0)|(mRC&0x1F));
        bytes[1] = mPT;
        bytes[2] = 0;                        // Length H16
        bytes[3] = (byte)(bytes.length/4-1); // Length L16  (32bit=1)

        // SSRC
        bytes[4] = (byte)((mSSRC0&0xFF000000) >> 24);
        bytes[5] = (byte)((mSSRC0&0x00FF0000) >> 16);
        bytes[6] = (byte)((mSSRC0&0x0000FF00) >> 8);
        bytes[7] = (byte)((mSSRC0&0x000000FF));
        return bytes;
    }
}
