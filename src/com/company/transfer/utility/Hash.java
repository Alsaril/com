package com.company.transfer.utility;

import java.util.Arrays;

public class Hash {
    public static final int LENGTH = 16;
    private final byte[] value;

    public Hash(byte[] value) {
        assert value.length == LENGTH;
        this.value = value;
    }

    public Hash(String str) {
        assert str.length() == LENGTH * Character.BYTES;
        this.value = Utility.hexToBytes(str);
    }

    public byte[] value() {
        return value;
    }

    @Override
    public String toString() {
        return Utility.bytesToHex(value);
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof Hash) && Arrays.equals(((Hash) obj).value, value);
    }

    @Override
    public int hashCode() {
        return ((value[0] ^ value[4] ^ value[8] ^ value[12]) << 3) +
                ((value[1] ^ value[5] ^ value[9] ^ value[13]) << 2) +
                ((value[2] ^ value[6] ^ value[10] ^ value[14]) << 1) +
                (value[3] ^ value[7] ^ value[11] ^ value[15]);
    }
}