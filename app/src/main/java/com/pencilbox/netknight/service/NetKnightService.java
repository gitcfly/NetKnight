package com.pencilbox.netknight.service;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.pencilbox.netknight.model.App;
import com.pencilbox.netknight.net.ByteBufferPool;
import com.pencilbox.netknight.net.Net2Device;
import com.pencilbox.netknight.net.NetNotifyThread;
import com.pencilbox.netknight.net.Packet;
import com.pencilbox.netknight.net.TCB;
import com.pencilbox.netknight.net.TCBCachePool;
import com.pencilbox.netknight.net.TCPInput;
import com.pencilbox.netknight.net.TCPOutput;
import com.pencilbox.netknight.net.UDPInput;
import com.pencilbox.netknight.net.UDPOutput;
import com.pencilbox.netknight.pcap.PCapFilter;
import com.pencilbox.netknight.utils.AppUtils;
import com.pencilbox.netknight.utils.MyLog;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.Selector;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by pencil-box on 16/6/17.
 * Vpnservice 核心类
 */
public class NetKnightService extends VpnService implements Runnable {
    //VPN转发的IP地址咯
    public static String  VPN_ADDRESS = "10.1.10.1";
    public static boolean vpnShouldRun = false;
    //从虚拟网卡拿到的文件描述符
    private ParcelFileDescriptor mInterface;
    //来自应用的请求的数据包
    private LinkedBlockingQueue<Packet> mOutputQueue;
    //即将发送至应用的数据包
    private LinkedBlockingQueue<ByteBuffer> mInputQueue;
    private LinkedBlockingQueue<Packet> udpOutputQueue;
    private LinkedBlockingQueue<ByteBuffer> udpInputQueue;
    //缓存的appInfo队列,请求被拦截的队列
    private LinkedBlockingQueue<App> mCacheAppInfo;
    //网络访问通知线程
    private NetNotifyThread mNetNotify;
    //网络输入输出
    private TCPInput mTCPInput;
    private TCPOutput mTCPOutput;
    private UDPInput mUdpInput;
    private UDPOutput mUdpOutput;
    private Net2Device tcp2Device;
    private Net2Device udp2Device;
    private FileChannel vpnInput;
    private FileChannel vpnOutput;
    private Selector mChannelSelector;
    private Selector UdpSelector;

    public static volatile boolean isRunning = false;
    public static volatile boolean isCalledByUser = false;

    @Override
    public void onCreate() {
        super.onCreate();
        MyLog.logd(this, "onCreate");
    }

    //建立vpn
    private void setupVpn(){
        //获取应用信息,并设置相应的包才动it
        List<App> appList = AppUtils.queryAppInfo(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Builder builder = new Builder();
            //只拦截需要拦截的应用
            for (int i = 0; i < appList.size(); i++) {
                App app = appList.get(i);
                if (app.isAccessVpn()) {
                    try {
                        builder = builder.addAllowedApplication(app.getPkgname());
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
            mInterface = builder.setSession("NetKnight").setBlocking(false).addAddress(VPN_ADDRESS,32).addRoute("0.0.0.0",0).establish();
            vpnInput = new FileInputStream(mInterface.getFileDescriptor()).getChannel();
            vpnOutput = new FileOutputStream(mInterface.getFileDescriptor()).getChannel();
        }else{
            Log.e("NetKnightService","当前版本的android 不支持vpnservice");
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MyLog.logd(this,"onStartCommand");
        setupVpn();
        try {
            mChannelSelector = Selector.open();
            UdpSelector = Selector.open();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCacheAppInfo = new LinkedBlockingQueue<>();
        mNetNotify = new NetNotifyThread(this,mCacheAppInfo);
        mOutputQueue = new LinkedBlockingQueue<>();
        mInputQueue = new LinkedBlockingQueue<>();
        udpInputQueue = new LinkedBlockingQueue<>();
        udpOutputQueue = new LinkedBlockingQueue<>();
        mTCPInput = new TCPInput(mInputQueue, mChannelSelector);
        mTCPOutput = new TCPOutput(mInputQueue, mOutputQueue, this, mChannelSelector, mCacheAppInfo);
        mUdpInput = new UDPInput(udpInputQueue, UdpSelector);
        mUdpOutput = new UDPOutput(udpOutputQueue, UdpSelector, this);
        tcp2Device = new Net2Device(mInputQueue, vpnOutput);
        udp2Device = new Net2Device(udpInputQueue, vpnOutput);
        //还是直接start呢?
        mNetNotify.start();
        mTCPInput.start();
        tcp2Device.start();
        mTCPOutput.start();
        mUdpInput.start();
        udp2Device.start();
        mUdpOutput.start();
        new Thread(this).start();
        return super.onStartCommand(intent, flags, startId);
    }



    @Override
    public void run() {
        MyLog.logd(this, "start");
        isRunning = true;
        ByteBuffer buffer2Net = null;
        boolean isDataSend = true;
        try {
            while (vpnShouldRun) {
                Thread.sleep(10);
                if(!isRunning){
                    Log.d("NetKnight","isRunning is false");
                    if(isCalledByUser){
                        close();
                        Log.d("NetKnight","stopSelf");
                    }
                    break;
                }
                //数据发送出去了,就get 新的咯
                if(isDataSend ) {
                    buffer2Net = ByteBufferPool.acquire();
                }else {
                    //未有数据发送,据清空咯
                    buffer2Net.clear();
                }
                int inputSize = vpnInput.read(buffer2Net);
                if (inputSize > 0) {
//                    MyLog.logd(this, "-----readData:-------size:" + inputSize);
                    //flip切换状态,由写状态转换成可读状态
                    buffer2Net.flip();
                    //从应用中发送的包
                    Packet packet2net = new Packet(buffer2Net);
//                    MyLog.logd(this, "--------data read----------size:" + packet2net.getPayloadSize());
//                    MyLog.logd(this, packet2net.toString());
                    if (packet2net.isTCP()) {
//                        MyLog.logd(this, "发送数据包  TCP");
                        //目前支持TCP
                        InetAddress desAddress = packet2net.ip4Header.destinationAddress;
                        int sourcePort = packet2net.tcpHeader.sourcePort;
                        int desPort = packet2net.tcpHeader.destinationPort;
                        String ipAndPort = desAddress.getHostAddress() + ":" + sourcePort + ":" + desPort;
                        //实现抓包功能咯
                        TCB tcb = TCBCachePool.getTCB(ipAndPort);
                        if(tcb !=null){
                            //方便包过滤使
                            //注意position 和 limit的位置,执行new Packet操作, position是到tcp头的位置的
                            int curPostion = buffer2Net.position();
                            int curLimit = buffer2Net.limit();
                            buffer2Net.position(buffer2Net.limit());
                            buffer2Net.limit(buffer2Net.capacity());
                            PCapFilter.filterPacket(buffer2Net,tcb.getAppId());
                            buffer2Net.position(curPostion);
                            buffer2Net.limit(curLimit);
                        }
                        try {
                            mOutputQueue.put(packet2net);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        isDataSend = true;
                    } else if (packet2net.isUDP()) {
                        MyLog.logd(this, "发送数据包 UDP:" + packet2net.ip4Header.toString());
                        try {
                            udpOutputQueue.put(packet2net);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        isDataSend = true;
                    } else {
                        MyLog.logd(this, "暂时不支持其他类型数据==" + packet2net.ip4Header.protocolNum);
                        isDataSend = false;
                    }
                }else{
                    //与其release 还不如直接复用
                    isDataSend = false;
//                    ByteBufferPool.release(buffer2Net);
                }
                //可减少内存抖动??
                if(!isDataSend) {
                    Thread.sleep(10);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                vpnInput.close();
                vpnOutput.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    /**
     * 关闭相关资源
     */
    public void close() {
        isRunning = false;
        mTCPInput.quit();
        mTCPOutput.quit();
        mNetNotify.quit();
        try {
            mChannelSelector.close();
            mInterface.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        MyLog.logd(this,"等待线程结束..");
        TCBCachePool.closeAll();
        mOutputQueue = null;
        mInputQueue = null;
        mCacheAppInfo = null;
        ByteBufferPool.clear();
    }

    //TODO 应该广播给activity调整那个item键的呢
    @Override
    public void onDestroy() {
        super.onDestroy();
        close();
        MyLog.logd(this, "onDestroy");
    }

}
