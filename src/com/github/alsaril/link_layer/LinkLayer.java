package com.github.alsaril.link_layer;

import com.github.alsaril.application_layer.ApplicationLayer;
import com.github.alsaril.interfaces.IApplicationLayer;
import com.github.alsaril.interfaces.ILinkLayer;
import com.github.alsaril.physical_layer.PhysicalLayer;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LinkLayer implements ILinkLayer, Runnable {
    private static final int TIMEOUT = 500;
    private final PhysicalLayer phys;
    private final InputStream is;
    private final OutputStream os;
    private ExecutorService ex = Executors.newSingleThreadExecutor();
    private volatile int lastAck = 0; // protected by instance lock
    private volatile int lastSend = 0; // protected by instance lock

    private volatile int lastRecv = 0;
    private volatile boolean needSend = false;

    private IApplicationLayer applicationLayer = null;

    public LinkLayer(String port, ApplicationLayer layer) throws EOFException {
        this.applicationLayer = layer;
        phys = PhysicalLayer.open(port);
        if (phys == null) {
            throw new EOFException();
        }
        is = phys.getInputStream();
        os = phys.getOutputStream();
        phys.setConnectionListener(this);
        ex.execute(this);
    }

    @Override
    public void close() {
        ex.shutdown();
        phys.close();
    }

    @Override
    public synchronized void send_msg(byte[] message) throws IOException {
        Frame frame = new Frame(++lastSend, lastRecv, message);
        for (int i = 0; i < 3 && lastAck != lastSend; i++) {
            frame.write(os);
            needSend = false;
            try {
                wait(TIMEOUT);
            } catch (InterruptedException e) {
                throw new IOException();
            }
        }
        if (lastAck != lastSend) {
            throw new IOException(lastAck + "get");
        }
    }

    private synchronized void sendFrame() {
        Frame frame = new Frame(++lastSend, lastRecv, new byte[]{});
        try {
            frame.write(os);
            needSend = false;
        } catch (IOException e) {
            applicationLayer.error();
        }
    }


    @Override
    public void stateChanged(ConnectionState state) {
        System.out.println("State changed to " + state);
        applicationLayer.stateChanged(state);
        if (state == ConnectionState.DISCONNECTED) {
            close();
        }
    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            Frame frame = Frame.read(is);
            if (needSend) {
                sendFrame();
            }
            synchronized (this) {
                if (frame == null) {
                    applicationLayer.error();
                    return;
                }
                if (frame.send == lastRecv + 1) {
                    if (frame.message.length != 0) {
                        applicationLayer.receive_msg(frame.message);
                    }
                    lastRecv = frame.send;
                    needSend = true;
                }
                if (frame.recv == lastSend) {
                    lastAck = frame.recv;
                    notify();
                    needSend = true;
                }
            }
        }
    }
}
