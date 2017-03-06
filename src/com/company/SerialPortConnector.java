package com.company;

import gnu.io.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.TooManyListenersException;

public class SerialPortConnector implements SerialPortEventListener {

    private SerialPort serialPort;
    private InputStream is;
    private OutputStream os;

    private PipedOutputStream pos;
    private PipedInputStream pis;

    private byte[] buffer = new byte[256];

    private SerialPortConnector(CommPortIdentifier cpi) throws SerialPortException {
        try {
            serialPort = (SerialPort) cpi.open("TerminalApp", 2000);
            is = serialPort.getInputStream();
            os = serialPort.getOutputStream();

            pos = new PipedOutputStream();
            pis = new PipedInputStream(pos);

            serialPort.addEventListener(this);
            serialPort.notifyOnDataAvailable(true);

            serialPort.setSerialPortParams(9600,
                    SerialPort.DATABITS_8,
                    SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE);
        } catch (IOException | PortInUseException | UnsupportedCommOperationException | TooManyListenersException e) {
            throw new SerialPortException(e.getMessage());
        }
    }

    public static SerialPortConnector open(String name) {
        Enumeration portList = CommPortIdentifier.getPortIdentifiers();
        while (portList.hasMoreElements()) {
            CommPortIdentifier portId = (CommPortIdentifier) portList.nextElement();
            if (name.equals(portId.getName())) {
                try {
                    return new SerialPortConnector(portId);
                } catch (SerialPortException e) {
                    return null;
                }
            }
        }
        return null;
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
            default:
                break;
        }
    }

    public InputStream getInputStream() {
        return pis;
    }

    public OutputStream getOutputStream() {
        return os;
    }

    public static List<String> getNames() {
        List<String> result = new ArrayList<>();
        Enumeration portList = CommPortIdentifier.getPortIdentifiers();
        while (portList.hasMoreElements()) {
            CommPortIdentifier portId = (CommPortIdentifier) portList.nextElement();
            result.add(portId.getName());
        }
        return result;
    }
}
