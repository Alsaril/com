package com.github.alsaril.interfaces;

public interface ConnectionListener {
    void stateChanged(ConnectionState state);

    enum ConnectionState {
        CONNECTED, DISCONNECTED;

        public static ConnectionState fromBoolean(boolean b) {
            return b ? CONNECTED : DISCONNECTED;
        }
    }
}
