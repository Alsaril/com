package com.company.transfer;

import java.io.*;
import java.util.HashMap;
import java.util.Scanner;

public class File {
    public final String hash;
    public final String path;
    public final String name;
    public final long size;
    public long date;
    private int block;
    private int showBlock = 0;
    private InputStream is = null;
    private OutputStream os = null;
    private boolean error = false;

    public File(String hash, String path, String name, long size, int block, long date) {
        this.hash = hash;
        this.path = path;
        this.name = name;
        this.size = size;
        this.block = block;
        this.date = date;
    }

    public File(String hash, java.io.File file) throws FileNotFoundException {
        this(hash, file.getAbsolutePath(), file.getName(), file.length(), 0, System.currentTimeMillis());
    }

    public File(java.io.File file) throws FileNotFoundException {
        this(Utility.fileHash(file), file.getAbsolutePath(), file.getName(), file.length(), 0, System.currentTimeMillis());
    }

    public File(String path) throws FileNotFoundException {
        this(new java.io.File(path));
    }

    public static HashMap<String, File> loadFiles(String path) {
        HashMap<String, File> result = new HashMap<>();
        try (Scanner sc = new Scanner(new BufferedInputStream(new FileInputStream(path)))) {
            while (sc.hasNext()) {
                File f = new File(sc.nextLine(), sc.nextLine(), sc.nextLine(), sc.nextLong(), sc.nextInt(), sc.nextLong());
                result.put(f.hash, f);
            }
        } catch (IOException e) {
        }
        return result;
    }

    public InputStream getIs() throws IOException {
        if (is == null) {
            try {
                is = new FileInputStream(path);
            } catch (IOException e) {
                error = true;
                throw e;
            }
        }
        return is;
    }

    public OutputStream getOs() throws IOException {
        if (os == null) {
            try {
                os = new FileOutputStream(path);
            } catch (IOException e) {
                error = true;
                throw e;
            }
        }
        return os;
    }

    public int getBlock() {
        return block;
    }

    public void incBlock() {
        block++;
    }

    public void incShowBlock() {
        showBlock++;
    }

    public int getProgress() {
        return (int) (100.0 * showBlock * Utility.BLOCK_SIZE / size);
    }

    public boolean isError() {
        return error;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof File && ((File) obj).hash.equals(hash);
    }

    public void setError() {
        error = true;
    }
}
