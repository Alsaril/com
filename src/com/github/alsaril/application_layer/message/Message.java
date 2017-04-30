package com.github.alsaril.application_layer.message;

import com.github.alsaril.application_layer.utility.Hash;

import java.nio.ByteBuffer;

public class Message {

    public final Hash hash;
    public final MessageType type;
    public final long position;
    public final byte[] data;

    public Message(Hash hash, MessageType type, long position, byte[] data) {
        this.hash = hash;
        this.type = type;
        this.position = position;
        this.data = data;
    }

    protected Message(byte[] message) {
        ByteBuffer bb = ByteBuffer.wrap(message);
        byte[] hashBytes = new byte[Hash.LENGTH];
        bb.get(hashBytes);
        hash = new Hash(hashBytes);
        type = MessageType.values()[bb.get()];
        position = bb.getLong();
        int length = bb.getInt();
        data = new byte[length];
        bb.get(data);
    }

    public static Message parse(byte[] message) {
        MessageType type = MessageType.values()[message[Hash.LENGTH]];
        if (type == MessageType.UPLOAD_REQUEST) {
            return new UploadRequestMessage(message);
        } else if (type == MessageType.UPLOAD_RESPONSE) {
            return new UploadResponseMessage(message);
        } else if (type == MessageType.DOWNLOAD_RESPONSE) {
            return new DownloadResponseMessage(message);
        }
        return new Message(message);
    }

    public byte[] toByte() {
        int length = Hash.LENGTH + Byte.BYTES + Long.BYTES + Integer.BYTES + length();
        ByteBuffer bb = ByteBuffer.allocate(length);
        bb.put(hash.value()).put((byte) type.ordinal()).putLong(position).putInt(length());
        if (data != null) {
            bb.put(data);
        }
        return bb.array();
    }

    public int length() {
        return data == null ? 0 : data.length;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("M: ");
        sb.append(hash.toString()).append(", ");
        sb.append(type.toString()).append(", ");
        sb.append(position).append(", ");
        if (data != null) {
            sb.append(data.length);
        } else {
            sb.append(0);
        }
        return sb.toString();
    }

    public enum MessageType {
        DATA,
        COMPLETE,
        ERROR,
        UPLOAD_REQUEST,
        UPLOAD_RESPONSE,
        DOWNLOAD_REQUEST,
        DOWNLOAD_RESPONSE,
        STOP_TRANSFER
    }
}
