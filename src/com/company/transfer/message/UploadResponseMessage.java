package com.company.transfer.message;

public class UploadResponseMessage extends Message {

    public final boolean accept;

    public UploadResponseMessage(Hash hash, boolean accept) {
        super(hash, MessageType.UPLOAD_RESPONSE, 0, new byte[]{accept ? (byte) 1 : 0});
        this.accept = accept;
    }

    public UploadResponseMessage(byte[] message) {
        super(message);
        accept = data[0] > 0;
    }
}
