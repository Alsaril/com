package com.github.alsaril.application_layer.utility;

import com.github.alsaril.MainWindow;

import javax.swing.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

public class Utility {
    public static final Charset charset = StandardCharsets.UTF_16;
    public static final int BLOCK_SIZE = 1024 * 4;
    private static final char[] hexArray = "0123456789ABCDEF".toCharArray();
    private static final HashMap<Hash, File> files = new HashMap<>();
    private static final String[] names = {"B", "KB", "MB", "GB"};
    public static String rootPath = "";
    private static String configPath;

    public static HashMap<Hash, File> getFiles(String config) {
        configPath = config;
        if (new java.io.File(config).exists()) {
            try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(configPath)))) {
                rootPath = dis.readUTF();
                int length = dis.readInt();
                for (int i = 0; i < length; i++) {
                    File f = File.read(dis);
                    if (f.hash.hasValue()) {
                        files.put(f.hash, f);
                    }
                }
            } catch (IOException e) {
            }
        }
        return files;
    }

    public static void save() {
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(configPath)))) {
            dos.writeUTF(rootPath);
            dos.writeInt(files.size());
            for (File f : files.values()) {
                f.write(dos);
            }
            dos.flush();
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

    public static Hash fileHash(java.io.File file, ProgressListener l) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        long length = file.length();
        long pos = 0;
        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[BLOCK_SIZE];
            while (true) {
                int read = is.read(buffer);
                if (read == -1) break;
                md.update(buffer, 0, read);
                pos += read;
                if (l != null) {
                    l.progress((double) pos / length);
                }
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

    public static void showMessage(String s, MainWindow window) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(window.getFrame(), s, "Сообщение", JOptionPane.INFORMATION_MESSAGE));
    }

    public static String unit(long value) {
        int index = 0;
        while (value >= 1024) {
            value >>>= 10;
            index++;
        }
        if (index > 3) {
            return "Infinity";
        }
        return String.format("%d %s", value, names[index]);
    }
}
