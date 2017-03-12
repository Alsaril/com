package com.company.transfer;

import java.nio.ByteBuffer;

public class UploadRequestMessage extends Message {

    public final String name;
    public final long size;

    public UploadRequestMessage(Hash hash, String name, long size) {
        super(hash,
                MessageType.UPLOAD_REQUEST,
                0,
                ByteBuffer.allocate(Long.BYTES + name.length() * Character.BYTES)
                        .putLong(size)
                        .put(name.getBytes())
                        .array());
        this.name = name;
        this.size = size;
    }

    public UploadRequestMessage(byte[] message) {
        super(message);
        ByteBuffer bb = ByteBuffer.wrap(data);
        size = bb.getLong();
        byte[] bytes = new byte[bb.remaining()];
        bb.get(bytes);
        name = Utility.trimZeros(new String(bytes));
    }
}
