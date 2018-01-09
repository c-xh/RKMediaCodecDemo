package com.rockchip.rkmediacodecdemo.rtsp;

import java.io.IOException;

/**
 * Created by zhanghao on 2017/1/6.
 */

public class AACPackage {
    public byte[] ADTS = {(byte)0xFF, (byte)0xF1, 0x00, 0x00, 0x00, 0x00, (byte)0xFC};
    public int AU_HEADER_LENGTH = 2;
    public byte[] data;

    public AACPackage() {

    }

    public AACPackage(int samplerate, int channel, int bit) {
        switch(samplerate)
        {
            case  16000:
                ADTS[2] = 0x60;
                break;
            case  32000:
                ADTS[2] = 0x54;
                break;
            case  44100:
                ADTS[2] = 0x50;
                break;
            case  48000:
                ADTS[2] = 0x4C;
                break;
            case  96000:
                ADTS[2] = 0x40;
                break;
            default:
                break;
        }
        ADTS[3] = (channel==2)?(byte)0x80:0x40;
    }

    public AACPackage getCompleteAACPackage(RTPPackage rtp) throws IOException {
        AACPackage aac = new AACPackage(44100, 2, 16); // TODO:: audio format
        aac.AU_HEADER_LENGTH = 2;//(rtp.csrc[0]&0xFF)<<8 | (rtp.csrc[1]&0xFF);
        //if (aac.AU_HEADER_LENGTH != 2) throw new IOException("unknown AU_HEADER_LENGTH!" + rtp.csrc[0] + ":" + rtp.csrc[1]);
        final int len = rtp.csrc.length - 2 - aac.AU_HEADER_LENGTH + aac.ADTS.length;
        int plen = (len << 5)|0x1F;
        aac.ADTS[4] = (byte)(plen >> 8);
        aac.ADTS[5] = (byte)(plen&0xFF);
        //aac.data = new byte[len];
        //System.arraycopy(aac.ADTS, 0, aac.data, 0, aac.ADTS.length);
        //System.arraycopy(rtp.csrc, 2 + aac.AU_HEADER_LENGTH, aac.data, aac.ADTS.length, rtp.csrc.length - 2 - aac.AU_HEADER_LENGTH);

        aac.data = new byte[len-aac.ADTS.length];
        System.arraycopy(rtp.csrc, 2 + aac.AU_HEADER_LENGTH, aac.data, 0, rtp.csrc.length - 2 - aac.AU_HEADER_LENGTH);
        return aac;
    }
}
