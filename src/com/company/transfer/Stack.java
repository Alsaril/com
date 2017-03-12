package com.company.transfer;

import com.company.transfer.interfaces.IApplicationLayer;
import com.company.transfer.interfaces.ILinkLayer;
import com.company.transfer.interfaces.IPhysicalLayer;

import java.io.IOException;

public class Stack implements IPhysicalLayer, ILinkLayer, IApplicationLayer {
    private IApplicationLayer applicationLayer;
    private ILinkLayer linkLayer;
    private IPhysicalLayer physicalLayer;

    public Stack() {

    }

    @Override
    public void receive_msg(byte[] message) {

    }

    @Override
    public void error_appl() {

    }

    @Override
    public void start_appl() {

    }

    @Override
    public void receive_byte(byte b) {

    }

    @Override
    public void send_msg(byte[] message) throws IOException {

    }

    @Override
    public void error_lnk() {

    }

    @Override
    public void start_lnk() {

    }

    @Override
    public void send_byte(byte[] frame) throws IOException {

    }

    @Override
    public void start_phys() {

    }
}
