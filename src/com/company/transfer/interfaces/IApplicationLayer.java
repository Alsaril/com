package com.company.transfer.interfaces;

public interface IApplicationLayer {
    void receive_msg(byte[] message);

    void error_appl();

    void start_appl();
}
