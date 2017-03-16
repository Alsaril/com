package com.company.transfer.utility;

import com.company.transfer.message.Message;

import java.io.*;

public class File {
    public final Message.Hash hash;
    public final String path;
    public final String name;
    public final long size;
    public long date;
    private int block;
    private int showBlock = 0;
    private FileStatus status = FileStatus.REQUEST;
    private InputStream is = null;
    private OutputStream os = null;
    private boolean error = false;

    public File(Message.Hash hash, String path, String name, long size, int block, long date, FileStatus status) {
        this.hash = hash;
        this.path = path;
        this.name = name;
        this.size = size;
        this.block = block;
        this.date = date;
        this.status = status;
        showBlock = block;
    }

    public File(Message.Hash hash, java.io.File file) throws FileNotFoundException {
        this(hash, file.getAbsolutePath(), file.getName(), file.length(), 0, System.currentTimeMillis(), FileStatus.REQUEST);
    }

    public File(java.io.File file) throws FileNotFoundException {
        this(Utility.fileHash(file), file.getAbsolutePath(), file.getName(), file.length(), 0, System.currentTimeMillis(), FileStatus.REQUEST);
    }

    public File(String path) throws FileNotFoundException {
        this(new java.io.File(path));
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

    public FileStatus getStatus() {
        return status;
    }

    public void setStatus(FileStatus status) {
        this.status = status;
    }

    public boolean readyToTransfer() {
        return block - showBlock < 20;
    }

    public enum FileStatus {
        REQUEST, TRANSFER, DECLINED, COMPLETE, ERROR
    }
}
