package com.company.transfer.interfaces;

import java.io.IOException;

public interface ILinkLayer {
    void receive_byte(byte b);

    void send_msg(byte[] message) throws IOException;

    void error_lnk();

    void start_lnk();
}
