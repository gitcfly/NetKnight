package com.pencilbox.netknight.net;

import android.util.Log;

import com.pencilbox.netknight.service.NetKnightService;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.BlockingQueue;

public class Net2Device extends Thread {
    private static final String TAG = UDPInput.class.getSimpleName();
    private BlockingQueue<ByteBuffer> mInputQueue;
    private FileChannel vpnOut;

    public Net2Device(BlockingQueue<ByteBuffer> mInputQueue, FileChannel vpnOut) {
        this.mInputQueue = mInputQueue;
        this.vpnOut = vpnOut;
    }

    @Override
    public void run() {
        while (NetKnightService.vpnShouldRun) {
            try {
                //将数据返回到应用中
                ByteBuffer buffer4Net = mInputQueue.poll();
                if (buffer4Net != null) {
                    //将limit=position position = 0 开始读操作
                    buffer4Net.flip();
                    while (buffer4Net.hasRemaining()) {
                        try {
                            vpnOut.write(buffer4Net);
                        } catch (Exception e) {
                            e.printStackTrace();
                            Thread.sleep(5);
                        }
                    }
                    ByteBufferPool.release(buffer4Net);
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    }
}
