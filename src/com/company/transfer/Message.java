package com.company.transfer;

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

    public enum MessageType {
        DATA(0),
        COMPLETE(1),
        BLOCK_RECEIVE(2),
        UPLOAD_REQUEST(3),
        UPLOAD_RESPONSE(4),
        DOWNLOAD_REQUEST(5),
        DOWNLOAD_RESPONSE(6),
        STOP_TRANSFER(7);

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
