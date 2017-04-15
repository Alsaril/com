package com.company.transfer.message;

import com.company.transfer.utility.Hash;

public class UploadResponseMessage extends Message {

    public final boolean accept;

    public UploadResponseMessage(Hash hash, boolean accept) {
        this(hash, 0, accept);
    }

    public UploadResponseMessage(Hash hash, long position, boolean accept) {
        super(hash, MessageType.UPLOAD_RESPONSE, position, new byte[]{accept ? (byte) 1 : 0});
        this.accept = accept;
    }

    public UploadResponseMessage(byte[] message) {
        super(message);
        accept = data[0] > 0;
    }
}
