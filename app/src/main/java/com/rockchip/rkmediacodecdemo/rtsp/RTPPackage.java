package com.rockchip.rkmediacodecdemo.rtsp;

/**
 * Created by zhanghao on 2016/12/30.
 */

public class RTPPackage {
    private static final String TAG = "RTPPackage";

    public static final int PTYPE_DYNAMIC_MIN = 96;
    public static final int PTYPE_DYNAMIC_MAX = 127;

    public byte ver;
    public byte pillow;
    public byte extend;
    public byte csrcCnt;
    public byte mark;
    public byte ptype;
    public int sequenceNumber;
    public long timestamp;
    public int ssrc;
    public byte[] csrc;
/*
    ver             版本号（V）：2比特，用来标志使用的RTP版本。
    pillow          填充位（P）：1比特，如果该位置位，则该RTP包的尾部就包含附加的填充字节。
    extend          扩展位（X）：1比特，如果该位置位的话，RTP固定头部后面就跟有一个扩展头部。
    csrcCnt         CSRC计数器（CC）：4比特，含有固定头部后面跟着的CSRC的数目。
    mark            标记位（M）：1比特,该位的解释由配置文档（Profile）来承担.
    ptype           载荷类型（PT）：7比特，标识了RTP载荷的类型。
    sequenceNumber  序列号（SN）：16比特，发送方在每发送完一个RTP包后就将该域的值增加1，
                            接收方可以由该域检测包的丢失及恢复包序列。序列号的初始值是随机的。
    timestamp       时间戳：32比特，记录了该包中数据的第一个字节的采样时刻。在一次会话开始时，时间戳初始化成一个初始值。
                            即使在没有信号发送时，时间戳的数值也要随时间而不断地增加（时间在流逝嘛）。x`
                            时间戳是去除抖动和实现同步不可缺少的。
    ssrc            同步源标识符(SSRC)：32比特，同步源就是指RTP包流的来源。在同一个RTP会话中不能有两个相同的SSRC值。
                            该标识符是随机选取的 RFC1889推荐了MD5随机算法。
    csrc            贡献源列表（CSRC List）：0～15项，每项32比特，用来标志对一个RTP混合器产生的新包有贡献的所有RTP包的源。
                            由混合器将这些有贡献的SSRC标识符插入表中。SSRC标识符都被列出来，以便接收端能正确指出交谈双方的身份。
*/

    /*
       0               1               2               3
       0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |V=2|P|X|  CC   |M|     PT      |       sequence number         |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                           timestamp                           |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |           synchronization source (SSRC) identifier            |
       +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
       |            contributing source (CSRC) identifiers             |
       |                             ....                              |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    */
    public RTPPackage create(byte[] buf, int size){
        RTPPackage rtpPack = new RTPPackage();
        // byte 0
        rtpPack.ver = (byte)((buf[0]&0xC0) >> 6);
        rtpPack.pillow = (byte)((buf[0]&0x20) >> 5);
        rtpPack.extend = (byte)((buf[0]&0x10) >> 4);
        rtpPack.csrcCnt = (byte)(buf[0]&0xF);

        // byte 1
        rtpPack.mark = (byte)((buf[1]&0x80) >> 7);
        rtpPack.ptype = (byte)(buf[1]&0x7F);

        // byte 2-3
        rtpPack.sequenceNumber = (((buf[2] & 0xFF) << 8) | (buf[3] & 0xFF));

        // byte 4
        rtpPack.timestamp = ((long)(buf[4] & 0xFF) << 24) | ((long)(buf[5]&0xFF) << 16) | ((long)(buf[6]&0xFF) << 8) | (long)(buf[7]&0xFF);

        // byte 5
        rtpPack.ssrc = ((buf[8]&0xFF) << 24) | ((buf[9]&0xFF) << 16) | ((buf[10]&0xFF) << 8) | (buf[11]&0xFF);

        // byte 6
        rtpPack.csrc = new byte[size-12];
        System.arraycopy(buf, 12, rtpPack.csrc, 0, size - 12);

        return rtpPack;
    }

    public String toString(){
        return "[V="+ver+" csrcCnt="+csrcCnt + " ptype="+ ptype +" sequence num="+sequenceNumber + " timestamp=" + timestamp + "]";
    }
}
