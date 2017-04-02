package com.company.transfer.interfaces;

public interface IApplicationLayer {
    void receive_msg(byte[] message, int length);

    void error_appl(String error);

    void start_appl();
}
