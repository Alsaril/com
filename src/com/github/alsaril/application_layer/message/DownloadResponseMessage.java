package com.github.alsaril.application_layer.message;

import com.github.alsaril.application_layer.utility.Hash;

public class DownloadResponseMessage extends Message {

    public final boolean accept;

    public DownloadResponseMessage(Hash hash, boolean accept) {
        super(hash, MessageType.DOWNLOAD_RESPONSE, 0, new byte[]{accept ? (byte) 1 : 0});
        this.accept = accept;
    }

    public DownloadResponseMessage(byte[] message) {
        super(message);
        accept = data[0] > 0;
    }
}
