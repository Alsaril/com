package com.github.alsaril.interfaces;

public interface IApplicationLayer extends ConnectionListener {
    void init();
    void receive_msg(byte[] message);

    void error();
}
