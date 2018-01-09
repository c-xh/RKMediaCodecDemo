package com.rockchip.rkmediacodecdemo.rtsp;

import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by zhanghao on 2016/12/30.
 */

public class NALUnit {
    private static final String TAG = "NALUnit";
    private static final int NALU_START_FLAG_NULL = 0;
    private static final int NALU_START_FLAG_3 = 3;
    private static final int NALU_START_FLAG_4 = 4;

    public static final int NALU_HEADER_TYPE_UNKNOWN = 0;
    public static final int NALU_HEADER_TYPE_SLICE_NON_IDR = 1;
    public static final int NALU_HEADER_TYPE_SLICE_IDR = 5;
    public static final int NALU_HEADER_TYPE_FU_A = 28;

    public static long sNALU_Sequece_Number = 17; /* just for video */

    public long sequece_number = 0;
    public int nal_unit_type = 0;

    public int fua_nal_unit_type = 0;
    public int first_mb_in_slice = -1;
    public int fua_first_mb_in_slice = -1;
    public boolean fu_isStart = false;
    public boolean fu_isEnd = false;

    public byte[] data = null;
    private List<NALUnit> mFUADataWorker = null;
/*
* 取一段码流分析如下：
    80 60 01 0f 00 0e 10 00 00 00 00 00 7c 85 88 82 €`..........|???
    00 0a 7f ca 94 05 3b 7f 3e 7f fe 14 2b 27 26 f8 ...??.;.>.?.+'&?
    89 88 dd 85 62 e1 6d fc 33 01 38 1a 10 35 f2 14 ????b?m?3.8..5?.
    84 6e 21 24 8f 72 62 f0 51 7e 10 5f 0d 42 71 12 ?n!$?rb?Q~._.Bq.
    17 65 62 a1 f1 44 dc df 4b 4a 38 aa 96 b7 dd 24 .eb??D??KJ8????$

    前12字节是RTP Header
    7c是FU indicator
    85是FU Header
    FU indicator（0x7C）和FU Header（0x85）换成二进制如下
    0111 1100 1000 0101
    按顺序解析如下：
    0           是F
    11          是NRI
    11100         是FU Type，这里是28，即FU-A

    1            是S，Start，说明是分片的第一包
    0           是E，End，如果是分片的最后一包，设置为1，这里不是
    0           是R，Remain，保留位，总是0
    00101        是NAl Type，这里是5，说明是关键帧
* */
    /* 分片单元指示
        NALU_HEADER
    +---------------+
    |0|1|2|3|4|5|6|7|
    +-+-+-+-+-+-+-+-+
    |F|NRI|  Type   |
    +---------------+*/

    /* 分片单元头
        FU_HEADER
    +---------------+
    |0|1|2|3|4|5|6|7|
    +-+-+-+-+-+-+-+-+
    |S|E|R|  Type   |
    +---------------+*/
    public  NALUnit parseHeader(RTPPackage rtp) throws IOException {
        NALUnit unit = new NALUnit();
        unit.nal_unit_type = rtp.csrc[0]&0x1F;

        if (unit.nal_unit_type == NALU_HEADER_TYPE_SLICE_NON_IDR ||
                unit.nal_unit_type == NALU_HEADER_TYPE_SLICE_IDR) {
            unit.first_mb_in_slice = getExpGolomb(rtp.csrc[1]);
        } else if (unit.nal_unit_type == NALU_HEADER_TYPE_FU_A) {
            unit.fu_isStart = (rtp.csrc[1]&0x80) != 0;
            unit.fu_isEnd = (rtp.csrc[1]&0x40) !=0;
            unit.fua_nal_unit_type = rtp.csrc[1]&0x1F;
            if (unit.fua_nal_unit_type == NALU_HEADER_TYPE_SLICE_NON_IDR ||
                    unit.fua_nal_unit_type == NALU_HEADER_TYPE_SLICE_IDR) {
                unit.fua_first_mb_in_slice = getExpGolomb(rtp.csrc[2]);
            }
        }
        return unit;
    }

    private void filldata(byte[] buf, int startFlagLength) throws IOException {
        data = new byte[buf.length + startFlagLength];
        for (int f=0; f<startFlagLength; f++) {
            if (f == startFlagLength-1) {
                data[f] = (byte)0x1;
            } else {
                data[f] = (byte)0x0;
            }
        }
        System.arraycopy(buf, 0, data, startFlagLength, buf.length);
    }

    public  NALUnit getCompleteNALUnit(RTPPackage rtp) throws IOException {
        NALUnit unit = parseHeader(rtp);
        unit.data = rtp.csrc;
        unit.sequece_number = rtp.sequenceNumber;
        if (unit.nal_unit_type == NALU_HEADER_TYPE_FU_A) {
            if (unit.fu_isStart && !unit.fu_isEnd) {
                mFUADataWorker = new ArrayList<>();
                mFUADataWorker.add(unit);
                return null;
            } else if (unit.fu_isEnd && !unit.fu_isStart) {
                if (mFUADataWorker == null) {
                    return null;
                }
                mFUADataWorker.add(unit);

                int length = 5; // add 4byte start flag + NALU_header
                for (NALUnit b: mFUADataWorker) {
                    length += (b.data.length-2); // reduce NALU_header & fua_header
                }

//                Log.d(TAG, "complexUnit.data.length:" + length + "              mFUADataWorker.get(0).data[].length" + mFUADataWorker.get(0).data.length);
                NALUnit complexUnit = new NALUnit();
                complexUnit.nal_unit_type = unit.fua_nal_unit_type;
                complexUnit.first_mb_in_slice = mFUADataWorker.get(0).fua_first_mb_in_slice;
                complexUnit.data = new byte[length];
                complexUnit.data[0] = 0x0;
                complexUnit.data[1] = 0x0;
                complexUnit.data[2] = 0x0;
                complexUnit.data[3] = 0x1;
                complexUnit.data[4] = (byte)((mFUADataWorker.get(0).data[0] & 0xE0) | (mFUADataWorker.get(0).data[1] & 0x1F));
                //Log.d("Rockchip", ">> fua type:"+unit.fua_nal_unit_type + " ; 4: " + complexUnit.data[4]);
                complexUnit.sequece_number = ++sNALU_Sequece_Number; // set sNALU_Sequece_Number
                //Log.d(TAG," complexUnit.sequece_number = " + complexUnit.sequece_number);

                int offset = 5;
                long checkSequeceNum = -1;
                for (int i=0; i<mFUADataWorker.size(); i++) {
                    if (checkSequeceNum != -1 && checkSequeceNum != mFUADataWorker.get(i).sequece_number - 1) { // SN check fail
                        Log.w(TAG, "uncompleted fu-a packet! drop it!");
                        complexUnit = null;
                        break;
                    }
                    System.arraycopy(mFUADataWorker.get(i).data, 2, complexUnit.data, offset, mFUADataWorker.get(i).data.length-2);
                    offset+=mFUADataWorker.get(i).data.length-2;
                    checkSequeceNum = mFUADataWorker.get(i).sequece_number;
                }
                if (complexUnit!=null && offset != complexUnit.data.length) Log.e(TAG, "Wrong Size!");
                mFUADataWorker.clear();
                mFUADataWorker = null;
                return complexUnit;
            } else if (!unit.fu_isStart && !unit.fu_isEnd){
                if (mFUADataWorker != null) {
                    mFUADataWorker.add(unit);
                } else {

                }
                return null;
            } else {
                throw new IOException("wrong fu_a data.");
            }
        } else if (unit.nal_unit_type > 0 && unit.nal_unit_type < 13) {
            return create(rtp);
        } else if (unit.nal_unit_type == 31){ /* Rockchip End Frame Flag */
            return create(rtp);
        } else {
            throw new IOException("unknown nalu type :"+unit.nal_unit_type);
        }
    }

    private  NALUnit create(RTPPackage rtp) throws IOException {
        NALUnit unit = parseHeader(rtp);
        unit.sequece_number = ++sNALU_Sequece_Number; // set sNALU_Sequece_Number
        //Log.d(TAG," unit.sequece_number = " + unit.sequece_number);

        if (unit.first_mb_in_slice > 0){
            unit.filldata(rtp.csrc, NALU_START_FLAG_3);
        } else { /* 0 or -1*/
            unit.filldata(rtp.csrc, NALU_START_FLAG_4);
        }
        return unit;
    }

    public int getLength() {
        return data.length;
    }

    public  int getExpGolomb(byte buf) {
        return (buf & 0x80) != 0 ? 0 : 1;
    }
}
