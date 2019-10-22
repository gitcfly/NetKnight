/*
 ** Copyright 2015, Mohamed Naufal
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **     http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 */

package com.pencilbox.netknight.net;

import android.net.VpnService;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

public class UDPOutput extends Thread {
    private static final String TAG = UDPOutput.class.getSimpleName();
    private static final int MAX_CACHE_SIZE = 50;

    private VpnService vpnService;
    private LinkedBlockingQueue<Packet> deviceToNetworkUDPQueue;
    private Selector selector;

    private LRUCache<String, DatagramChannel> channelCache =
            new LRUCache<>(MAX_CACHE_SIZE, new LRUCache.CleanupCallback<String, DatagramChannel>() {
                @Override
                public void cleanup(Map.Entry<String, DatagramChannel> eldest) {
                    closeChannel(eldest.getValue());
                }
            });

    public UDPOutput(LinkedBlockingQueue<Packet> inputQueue, Selector selector, VpnService vpnService) {
        this.deviceToNetworkUDPQueue = inputQueue;
        this.selector = selector;
        this.vpnService = vpnService;
    }

    @Override
    public void run() {
        Log.i(TAG, "Started");
        Thread currentThread = Thread.currentThread();
        while (!currentThread.isInterrupted()) {
            try {
                Packet currentPacket;
                // TODO: Block when not connected
                do {
                    currentPacket = deviceToNetworkUDPQueue.poll();
                    if (currentPacket != null)
                        break;
                } while (!currentThread.isInterrupted());
                InetAddress destinationAddress = currentPacket.ip4Header.destinationAddress;
                int destinationPort = currentPacket.udpHeader.destinationPort;
                int sourcePort = currentPacket.udpHeader.sourcePort;
                String ipAndPort = destinationAddress.getHostAddress() + ":" + destinationPort + ":" + sourcePort;
                DatagramChannel outputChannel = channelCache.get(ipAndPort);
                if (outputChannel == null) {
                    outputChannel = DatagramChannel.open();
                    vpnService.protect(outputChannel.socket());
                    try {
                        outputChannel.connect(new InetSocketAddress(destinationAddress, destinationPort));
                    } catch (IOException e) {
                        Log.e(TAG, "Connection error: " + ipAndPort, e);
                        closeChannel(outputChannel);
                        ByteBufferPool.release(currentPacket.backingBuffer);
                        continue;
                    }
                    outputChannel.configureBlocking(false);
                    currentPacket.swapSourceAndDestination();
                    selector.wakeup();
                    outputChannel.register(selector, SelectionKey.OP_READ, currentPacket);
                    channelCache.put(ipAndPort, outputChannel);
                }
                try {
                    ByteBuffer payloadBuffer = currentPacket.backingBuffer;
                    while (payloadBuffer.hasRemaining())
                        outputChannel.write(payloadBuffer);
                } catch (IOException e) {
                    Log.e(TAG, "Network write error: " + ipAndPort, e);
                    channelCache.remove(ipAndPort);
                    closeChannel(outputChannel);
                }
                ByteBufferPool.release(currentPacket.backingBuffer);
            } catch (Exception e) {
                Log.e(TAG, "服務停止", e);
            }
        }
        closeAll();
    }

    private void closeAll() {
        Iterator<Map.Entry<String, DatagramChannel>> it = channelCache.entrySet().iterator();
        while (it.hasNext()) {
            closeChannel(it.next().getValue());
            it.remove();
        }
    }

    private void closeChannel(DatagramChannel channel) {
        try {
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
