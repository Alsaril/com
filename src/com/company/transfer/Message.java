package com.company.transfer;

import java.nio.ByteBuffer;

public class Message {

    public final String hash;
    public final MessageType type;
    public final int block;
    public final byte[] data;

    public Message(String hash, MessageType type, int block, byte[] data) {
        this.hash = hash;
        this.type = type;
        this.block = block;
        this.data = data;
    }

    public Message(byte[] message) {
        hash = new String(message, 0, 32);
        type = MessageType.values()[message[32]];
        block = ByteBuffer.wrap(message, 33, 4).getInt();
        int length = message.length - 33 - 4;
        data = new byte[length];
        System.arraycopy(message, 32 + 1 + 4, data, 0, data.length);
    }

    public byte[] toByte() {
        assert type.id >= 0;
        int length = 32 + 1 + 4 + (data == null ? 0 : data.length);
        ByteBuffer bb = ByteBuffer.allocate(length);
        bb.put(hash.getBytes()).put(type.id).putInt(block);
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


}
