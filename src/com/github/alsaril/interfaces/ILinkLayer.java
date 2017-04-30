package com.github.alsaril.interfaces;

import java.io.IOException;

public interface ILinkLayer extends ConnectionListener {
    void close();
    void send_msg(byte[] message) throws IOException;
}
