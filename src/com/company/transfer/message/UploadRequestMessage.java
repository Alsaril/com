package com.company.transfer.message;

import com.company.transfer.utility.Hash;
import com.company.transfer.utility.Utility;

import java.nio.ByteBuffer;

public class UploadRequestMessage extends Message {

    public final String name;
    public final long size;

    public UploadRequestMessage(Hash hash, String name, long size) {
        super(hash, MessageType.UPLOAD_REQUEST, 0,
                payload(name, size));
        this.name = name;
        this.size = size;
    }

    public UploadRequestMessage(byte[] message) {
        super(message);
        ByteBuffer bb = ByteBuffer.wrap(data);
        size = bb.getLong();
        byte[] bytes = new byte[bb.remaining()];
        bb.get(bytes);
        name = new String(bytes, Utility.charset);
    }

    private static byte[] payload(String name, long size) {
        byte[] bytes = name.getBytes(Utility.charset);

        return ByteBuffer.allocate(Long.BYTES + bytes.length)
                .putLong(size)
                .put(bytes)
                .array();
    }
}
