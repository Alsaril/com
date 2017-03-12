package com.company.transfer;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Utility {
    public static final int BLOCK_SIZE = 1024 * 4;
    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static String fileHash(java.io.File file) {
        MessageDigest md;
        long start = System.currentTimeMillis();
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[BLOCK_SIZE];
            while (true) {
                int read = is.read(buffer);
                if (read == -1) break;
                md.update(buffer, 0, read);
            }
        } catch (IOException e) {
            return "";
        }
        long end = System.currentTimeMillis();
        System.out.println("Hash " + (end - start) / 1000 + " s.");
        return bytesToHex(md.digest());
    }
}
