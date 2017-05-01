package com.github.alsaril.link_layer;

import com.github.alsaril.application_layer.utility.Utility;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

public class Frame {
    private static final byte TERMINAL = (byte) 0xff;
    private static final byte ESCAPE = (byte) 0xfe;
    private static final int HEADER_LENGTH = 16;
    public final int send;
    public final int recv;
    public final byte[] checksum;
    public final byte[] message;

    public Frame(int send, int recv, byte[] message) {
        this.send = send;
        this.recv = recv;
        this.checksum = calcChecksum(message);
        this.message = message;
    }

    public static Frame read(InputStream is) throws IOException {
        ArrayList<Byte> frame = new ArrayList<>();
        boolean escapeNext = false;
        while (true) {
            int read;
            read = is.read();
            if (read == -1) {
                throw new EOFException();
            }
            byte b = (byte) read;
            if (escapeNext) {
                frame.add(b);
                escapeNext = false;
            } else if (b == ESCAPE) {
                escapeNext = true;
            } else if (b == TERMINAL) {
                break;
            } else {
                frame.add(b);
            }
        }
        byte[] byteFrame = new byte[frame.size()];
        for (int i = 0; i < frame.size(); i++) {
            byteFrame[i] = frame.get(i);
        }

        byte[] encodedHeader = new byte[HEADER_LENGTH];
        byte[] message = new byte[byteFrame.length - HEADER_LENGTH];
        System.arraycopy(byteFrame, 0, encodedHeader, 0, encodedHeader.length);
        System.arraycopy(byteFrame, HEADER_LENGTH, message, 0, message.length);

        byte[] header = decode(encodedHeader);
        if (header == null) { // заголовок поврежден
            return null;
        }
        ByteBuffer headerBuffer = ByteBuffer.wrap(header);
        Frame result = new Frame(headerBuffer.getInt(), headerBuffer.getInt(), message);

        byte[] checksum = new byte[Integer.BYTES];
        headerBuffer.get(checksum);
        if (!Arrays.equals(result.checksum, checksum)) { // сумма не сошлась
            return null;
        }
        return result;
    }

    private static byte[] calcChecksum(byte[] message) {
        byte[] checksum = new byte[Integer.BYTES];
        Arrays.fill(checksum, (byte) 0);

        for (int i = 0; i < checksum.length; i++) //0,1,2,3
            for (int j = i; j < message.length; j += 4) //0,4,8  1,5,9  2,6,10  3,7,11
                checksum[i] = (byte) (checksum[i] ^ message[j]);  //сложение по модулю два

        return checksum;
    }

    private static byte[] decode(byte[] header) {
        ArrayList<Boolean> bits = new ArrayList<>();
        for (int i = 0; i < header.length * 8; i++) {
            bits.add((header[i / 8] & (1 << (7 - (i % 8)))) > 0);
        }
        bits.remove(0);  //удаление "бита четности"

        int n = 0;
        byte[] result = new byte[bits.size() / Byte.SIZE];
        boolean[] bufBool = getBooleans(bits);

        //проверка наличия ошибки по синдрому ошибки
        for (int j = 0; j < 7; j++) {
            int myInt = (bufBool[j]) ? 1 : 0;
            if (myInt != 0) {
                return null;  //при помощи кода Хэмминга найдена ошибка!!
            }
        }

        //если код Хэмминга показал, что ошибок нет
        //удаление проверочных битов Хэмминга
        bits.remove(63);
        bits.remove(31);
        bits.remove(15);
        bits.remove(7);
        bits.remove(3);
        bits.remove(1);
        bits.remove(0);

        for (Boolean b : bits) {
            result[n / 8] |= (b ? 1 : 0) << (7 - n % 8);
            n++;
        }
        return result;
    }

    private static boolean[] getBooleans(ArrayList<Boolean> bits) {
        boolean[][] bufMatrix = new boolean[127][7];
        for (int i = 1; i < 128; i++) {
            int a = i;
            int j = 0;
            int b;
            while (a != 0) {
                b = a % 2;
                if (b == 0) {
                    bufMatrix[i - 1][j] = false;
                } else if (b == 1) {
                    bufMatrix[i - 1][j] = true;
                }
                a /= 2;
                j++;
            }
        }

        boolean[] bufBool = new boolean[7];  //хранится сумма по модулю два от сумм произведений для строк r0-r6
        for (int j = 0; j < 7; j++) {
            int sum = 0;  //сумма произведений для строк r0-r6
            for (int i = 0; i < 127; i++) {
                int myInt1 = (bufMatrix[i][j]) ? 1 : 0;
                int myInt2 = bits.get(i) ? 1 : 0;
                sum = sum + myInt1 * myInt2;
            }

            bufBool[j] = sum % 2 != 0;
        }
        return bufBool;
    }

    public void write(OutputStream os) throws IOException {
        byte[] header = encode();
        byte[] frame = new byte[header.length + message.length];
        System.arraycopy(header, 0, frame, 0, header.length);
        System.arraycopy(message, 0, frame, header.length, message.length);

        for (byte b : frame) {
            if (b == TERMINAL || b == ESCAPE) {
                os.write(ESCAPE);
            }
            os.write(b);
        }
        os.write(TERMINAL);
    }

    private byte[] encode() {
        byte[] tripleNull = {0, 0, 0};  //в конце всегда три нулевых байта
        byte[] byteHeader = ByteBuffer.allocate(Integer.BYTES * 2 + checksum.length + tripleNull.length)
                .putInt(send)
                .putInt(recv)
                .put(checksum)
                .put(tripleNull)
                .array();  // два нулевых байта в конце. Убрать!

        ArrayList<Boolean> bits = new ArrayList<>();
        for (int i = 0; i < byteHeader.length * 8; i++) {
            bits.add((byteHeader[i / 8] & (1 << (7 - (i % 8)))) > 0);
        }

        //кодирование Хэммингом
        for (int i = 0; i < 7; i++) {
            bits.add((1 << i) - 1, false);
        }

        boolean[] bufBool = getBooleans(bits);


        bits.remove(0);
        bits.add(0, bufBool[0]);  //нулевые проверочные биты на местах с индексами,
        bits.remove(1);
        bits.add(1, bufBool[1]);  //равными степеням двойки - 1  (2^n - 1)
        bits.remove(3);
        bits.add(3, bufBool[2]);
        bits.remove(7);
        bits.add(7, bufBool[3]);
        bits.remove(15);
        bits.add(15, bufBool[4]);
        bits.remove(31);
        bits.add(31, bufBool[5]);
        bits.remove(63);
        bits.add(63, bufBool[6]);

        bits.add(0, false); // hack
        int n = 0;
        byte[] result = new byte[bits.size() / Byte.SIZE];
        for (Boolean b : bits) {
            result[n / 8] |= (b ? 1 : 0) << (7 - n % 8);
            n++;
        }

        return result;
    }

    @Override
    public String toString() {
        return send + " " + recv + " " + Utility.bytesToHex(checksum);
    }
}
