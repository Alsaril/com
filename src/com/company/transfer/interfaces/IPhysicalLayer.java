package com.company.transfer.interfaces;

import java.io.IOException;

public interface IPhysicalLayer {
    void send_byte(byte[] frame) throws IOException;

    void start_phys();
}
