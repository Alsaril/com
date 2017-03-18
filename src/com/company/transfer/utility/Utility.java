package com.company.transfer.utility;

import com.company.transfer.MainWindow;

import javax.swing.*;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

public class Utility {
    public static final int BLOCK_SIZE = 1024 * 4;
    private static final char[] hexArray = "0123456789ABCDEF".toCharArray();
    private static final HashMap<Hash, File> files = new HashMap<>();
    public static String rootPath = "";
    private static String configPath;

    public static HashMap<Hash, File> getFiles(String config) {
        configPath = config;
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(configPath)))) {
            rootPath = dis.readUTF();
            int length = dis.readInt();
            for (int i = 0; i < length; i++) {
                File f = new File(new Hash(dis.readUTF()),
                        dis.readUTF(),
                        dis.readUTF(),
                        dis.readLong(),
                        dis.readInt(),
                        dis.readLong(),
                        File.FileStatus.values()[dis.readInt()]);
                files.put(f.hash, f);
            }
        } catch (IOException e) {
        }
        return files;
    }

    //Message.Hash hash, String path, String name, long size, int block, long date, FileStatus status
    public static void save() {
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(configPath)))) {
            dos.writeUTF(rootPath);
            dos.writeInt(files.size());
            for (File f : files.values()) {
                dos.writeUTF(f.hash.toString());
                dos.writeUTF(f.path);
                dos.writeUTF(f.name);
                dos.writeLong(f.size);
                dos.writeInt(f.getBlock());
                dos.writeLong(f.date);
                dos.writeInt(f.getStatus().ordinal());
            }
        } catch (IOException e) {
        }
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public static Hash fileHash(java.io.File file) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[BLOCK_SIZE];
            while (true) {
                int read = is.read(buffer);
                if (read == -1) break;
                md.update(buffer, 0, read);
            }
        } catch (IOException e) {
            return null;
        }
        return new Hash(md.digest());
    }

    public static String trimZeros(String str) {
        int pos = str.indexOf(0);
        return pos == -1 ? str : str.substring(0, pos);
    }

    public static void showError(String s, MainWindow window) {
        JOptionPane.showMessageDialog(window.getFrame(), s);
    }
}
