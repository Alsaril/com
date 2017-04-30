package com.github.alsaril.physical_layer;

import com.github.alsaril.interfaces.ConnectionListener;
import gnu.io.*;

import java.io.*;
import java.util.*;

public class PhysicalLayer implements SerialPortEventListener {

    private static final List<String> portNames;

    static {
        List<String> result = new ArrayList<>();
        Enumeration portList = CommPortIdentifier.getPortIdentifiers();
        while (portList.hasMoreElements()) {
            CommPortIdentifier portId = (CommPortIdentifier) portList.nextElement();
            result.add(portId.getName());
        }
        portNames = Collections.unmodifiableList(result);
    }

    private SerialPort serialPort;
    private InputStream is;
    private OutputStream os;
    private PipedOutputStream pos;
    private PipedInputStream pis;
    private ConnectionListener listener = null;
    private byte[] buffer = new byte[256];

    private PhysicalLayer(CommPortIdentifier cpi) throws Exception {
        try {
            serialPort = (SerialPort) cpi.open("TerminalApp", 2000);
            is = serialPort.getInputStream();
            os = serialPort.getOutputStream();

            pos = new PipedOutputStream();
            pis = new PipedInputStream(pos);

            serialPort.addEventListener(this);
            serialPort.notifyOnDataAvailable(true);
            serialPort.notifyOnDSR(true);

            serialPort.setSerialPortParams(9600,
                    SerialPort.DATABITS_8,
                    SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE);

        } catch (IOException | PortInUseException | UnsupportedCommOperationException | TooManyListenersException e) {
            throw new Exception(e.getMessage());
        }
    }

    public static PhysicalLayer open(String name) {
        Enumeration portList = CommPortIdentifier.getPortIdentifiers();
        while (portList.hasMoreElements()) {
            CommPortIdentifier portId = (CommPortIdentifier) portList.nextElement();
            if (name.equals(portId.getName())) {
                try {
                    return new PhysicalLayer(portId);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    public static List<String> getPortNames() {
        return portNames;
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.listener = listener;
        if (listener != null && serialPort.isDSR()) {
            listener.stateChanged(ConnectionListener.ConnectionState.CONNECTED);
        }
    }

    public void close() {
        serialPort.close();
    }

    @Override
    public void serialEvent(SerialPortEvent event) {
        switch (event.getEventType()) {
            case SerialPortEvent.DATA_AVAILABLE:
                int read = 0;
                try {
                    while (is.available() > 0) {
                        int a = is.read(buffer, read, buffer.length - read);
                        if (a == -1) break;
                        read += a;
                        if (read == buffer.length) {
                            byte[] newBuffer = new byte[buffer.length * 2];
                            System.arraycopy(buffer, 0, newBuffer, 0, read);
                            buffer = newBuffer;
                        }
                    }
                    pos.write(buffer, 0, read);
                    pos.flush();
                } catch (IOException e) {
                    return;
                }
                break;
            case SerialPortEvent.DSR:
                if (listener != null) {
                    listener.stateChanged(ConnectionListener.ConnectionState.fromBoolean(event.getNewValue()));
                }
                break;
        }
    }

    public InputStream getInputStream() {
        return pis;
    }

    public OutputStream getOutputStream() {
        return os;
    }
}
