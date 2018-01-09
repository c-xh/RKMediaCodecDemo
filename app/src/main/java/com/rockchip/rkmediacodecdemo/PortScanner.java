package com.rockchip.rkmediacodecdemo;


import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * Created by cxh on 2017/7/31.
 */

public class PortScanner {

    private static final String TAG = "ContentValues";
    private static final boolean netdebug = false;
    private static final boolean DEBUG = true;
    private static int bindPort = 33333;
    Handler handler;

    public PortScanner() {
    }

    /**
     * 传入当前的handler
     *
     * @param handler handler
     */


    /**
     * 获取本机的IP
     *
     * @return Ip地址
     */
    public String getLocalHostIP() {
        String ip;
        try {
            /**返回本地主机。*/
            InetAddress addr = InetAddress.getLocalHost();
            /**返回 IP 地址字符串（以文本表现形式）*/
            ip = addr.getHostAddress();
        } catch (Exception ex) {
            ip = "";
        }
        return ip;
    }

    /**
     * 获取广播地址
     *
     * @return 广播地址
     */
    public String getBroadcast() throws SocketException {
        System.setProperty("java.net.preferIPv4Stack", "true");
        for (Enumeration<NetworkInterface> niEnum = NetworkInterface.getNetworkInterfaces(); niEnum.hasMoreElements(); ) {
            NetworkInterface ni = niEnum.nextElement();
            if (!ni.isLoopback()) {
                for (InterfaceAddress interfaceAddress : ni.getInterfaceAddresses()) {
                    if (interfaceAddress.getBroadcast() != null) {
                        return interfaceAddress.getBroadcast().toString().substring(1);
                    }
                }
            }
        }
        return "";
    }



    /**
     * 寻找一个可用的端口
     *
     * @return 可用的端口号
     */
    public synchronized static int StartLocalPort() {
        int AvailablePort = bindPort;
        while (true) {
            bindPort++;
            AvailablePort = bindPort;
            if (isPortAvailable(bindPort)) ;
            {
                bindPort++;
                if (isPortAvailable(bindPort)) ;
                {
                    break;
                }
            }
        }
        bindPort += 100;
        return AvailablePort;
    }

    /**
     * 绑定本机指定端口和IP地址（绑定失败抛出异常）
     *
     * @param host 待扫描IP
     * @param port 待扫描端口
     */
    private static void bindPort(String host, int port) throws Exception {
        Socket s = new Socket();
        s.bind(new InetSocketAddress(host, port));
        s.close();
    }

    /**
     * 检测本机传入的端口是否被占用
     *
     * @param port 待扫描端口
     * @return 是否被占用
     */
    private static boolean isPortAvailable(int port) {
        try {
            bindPort("0.0.0.0", port);
            bindPort(InetAddress.getLocalHost().getHostAddress(), port);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检测将要连接的IP端口是否可用
     *
     * @param ip      待加测的IP
     * @param Port    待检测的端口
     * @param timeout 连接超时时间
     * @return 是否被占用
     */
    private boolean TCPPortIsUsable(String ip, int Port, int timeout) {

        try {
            Socket socket;
            SocketAddress socketAddress;

            socket = new Socket();
            socketAddress = new InetSocketAddress(ip, Port);
            socket.connect(socketAddress, timeout);
            socket.close();
        } catch (IOException e) {
            Log.d(TAG, "IP： " + ip + "   端口：" + Port + "  关闭");
            return false;
        }
        return true;
    }
}
