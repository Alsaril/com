package com.company.transfer.message;

import com.company.transfer.utility.Utility;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class Message {

    public final Hash hash;
    public final MessageType type;
    public final int block;
    public final byte[] data;

    public Message(Hash hash, MessageType type, int block, byte[] data) {
        this.hash = hash;
        this.type = type;
        this.block = block;
        this.data = data;
    }

    protected Message(byte[] message) {
        ByteBuffer bb = ByteBuffer.wrap(message);
        byte[] hashBytes = new byte[Hash.LENGTH];
        bb.get(hashBytes);
        hash = new Hash(hashBytes);
        type = MessageType.values()[bb.get()];
        block = bb.getInt();
        data = new byte[bb.remaining()];
        bb.get(data);
    }

    public static Message parse(byte[] message) {
        MessageType type = MessageType.values()[message[Hash.LENGTH]];
        if (type == MessageType.UPLOAD_REQUEST) {
            return new UploadRequestMessage(message);
        } else if (type == MessageType.UPLOAD_RESPONSE) {
            return new UploadResponseMessage(message);
        }
        return new Message(message);
    }

    public byte[] toByte() {
        int length = Hash.LENGTH + Byte.BYTES + Integer.BYTES + (data == null ? 0 : data.length);
        ByteBuffer bb = ByteBuffer.allocate(length);
        bb.put(hash.value()).put(type.id).putInt(block);
        if (data != null) {
            bb.put(data);
        }
        return bb.array();
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer("M: ");
        sb.append(hash.toString()).append(", ");
        sb.append(type.toString()).append(", ");
        sb.append(block).append(", ");
        if (data != null) {
            sb.append(data.length);
        } else {
            sb.append(0);
        }
        return sb.toString();
    }

    public enum MessageType {
        DATA(0),
        COMPLETE(1),
        ERROR(2),
        BLOCK_RECEIVE(3),
        UPLOAD_REQUEST(4),
        UPLOAD_RESPONSE(5),
        DOWNLOAD_REQUEST(6),
        DOWNLOAD_RESPONSE(7),
        STOP_TRANSFER(8);

        public final byte id;

        MessageType(int id) {
            this.id = (byte) id;
        }
    }

    public static class Hash {
        public static final int LENGTH = 16;
        private final byte[] value;

        public Hash(byte[] value) {
            assert value.length == LENGTH;
            this.value = value;
        }

        public Hash(String str) {
            assert str.length() == LENGTH * Character.BYTES;
            this.value = Utility.hexToBytes(str);
        }

        public byte[] value() {
            return value;
        }

        @Override
        public String toString() {
            return Utility.bytesToHex(value);
        }

        @Override
        public boolean equals(Object obj) {
            return (obj instanceof Hash) && Arrays.equals(((Hash) obj).value, value);
        }

        @Override
        public int hashCode() {
            return ((value[0] ^ value[4] ^ value[8] ^ value[12]) << 3) +
                    ((value[1] ^ value[5] ^ value[9] ^ value[13]) << 2) +
                    ((value[2] ^ value[6] ^ value[10] ^ value[14]) << 1) +
                    (value[3] ^ value[7] ^ value[11] ^ value[15]);
        }
    }

}
