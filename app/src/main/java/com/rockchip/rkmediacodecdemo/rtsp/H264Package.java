package com.rockchip.rkmediacodecdemo.rtsp;

import android.util.Log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * Created by zhanghao on 2017/1/2.
 */

public class H264Package {
    private static final String TAG = "H264Package";
    H264Package sLastH264Worker = null;
    List<NALUnit> mNALU = null;
    Queue<H264Package> sCompleteH264Frame = new ArrayDeque<>(3);

    public H264Package() {
        mNALU = new ArrayList<>();
    }

    private void append(NALUnit unit) {
        mNALU.add(unit);
    }

    private boolean checkSN() {
        long last_sn = -1;
        for(int i=0; i<mNALU.size(); i++){
            if (i == 0)
                last_sn = mNALU.get(i).sequece_number;
            else {
                long cur_sn = mNALU.get(i).sequece_number;
                if (cur_sn != last_sn + 1) { // check fail
                    Log.w(TAG, "uncompleted h264 frame! drop it! cur_sn="+cur_sn+";last_sn="+last_sn);
                    return false;
                }
                last_sn = cur_sn;
            }
        }
        return true;
    }

    public  void sendNALUnit(NALUnit unit) {
        //Log.d("Rockchip", ">. sendNALUnit: " + unit.first_mb_in_slice);
        //H264Package tmp = null;
        if (unit.nal_unit_type == 31) { /* Rockchip End Frame */
//            if (DefaultConfig.DEFAULT_DEBUG_DELAY)
//                Log.d(DefaultConfig.TAG, "----------------- END FRAME ----------------");
//            if(sLastH264Worker != null) {
//                sCompleteH264Frame.add(sLastH264Worker);
//            }
            sLastH264Worker = null;
        } else if (unit.first_mb_in_slice == 0) {
            //tmp = sLastH264Worker;
            if(sLastH264Worker != null) {
                sCompleteH264Frame.add(sLastH264Worker);
                //Log.d("Rockchip", ">.0 sCompleteH264Frame add " + sLastH264Worker);
            }
            sLastH264Worker = new H264Package();
            sLastH264Worker.append(unit);
        } else if (unit.first_mb_in_slice == -1) {
            //ret = sLastH264Worker;
            if(sLastH264Worker != null) {
                sCompleteH264Frame.add(sLastH264Worker);
                //Log.d("Rockchip", ">.-1 sCompleteH264Frame add " + sLastH264Worker);
            }
            sLastH264Worker = new H264Package();
            sLastH264Worker.append(unit);
            sCompleteH264Frame.add(sLastH264Worker);
            //Log.d("Rockchip", ">.-1+ sCompleteH264Frame add " + sLastH264Worker);
            sLastH264Worker = null;
        } else {
            if (sLastH264Worker != null)
                sLastH264Worker.append(unit);
            else
                Log.w(TAG, "Got a broken 264 package !");
        }
    }

    public  H264Package getCompleteH264() {
        H264Package ret = sCompleteH264Frame.poll();
        //Log.d("Rockchip", ">. getCompleteH264 poll: " + ret);
        return ret;
    }


    public byte[] getBytes() {
        int size = 0;
        for (NALUnit unit: mNALU) {
            size += unit.getLength();
        }

        byte[] bytes = new byte[size];
        int offset = 0;
        for (int i=0; i<mNALU.size(); i++){
            System.arraycopy(mNALU.get(i).data, 0, bytes, offset, mNALU.get(i).getLength());
            offset += mNALU.get(i).getLength();
        }

        return bytes;
    }
}
