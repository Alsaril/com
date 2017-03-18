package com.company.transfer.utility;

import com.company.transfer.ApplicationLayer;
import com.company.transfer.message.Message;
import com.company.transfer.message.UploadRequestMessage;

import java.io.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class File {
    private static final Executor ex = Executors.newFixedThreadPool(1);
    public final String path;
    public final String name;
    public final long size;
    public final long date;
    public Hash hash = null;
    private int block;
    private FileStatus status;
    private InputStream is = null;
    private OutputStream os = null;
    private boolean error = false;

    public File(Hash hash, String path, String name, long size, int block, long date, FileStatus status) {
        this.hash = hash;
        this.path = path;
        this.name = name;
        this.size = size;
        this.block = block;
        this.date = date;
        this.status = status;
    }

    public File(Hash hash, java.io.File file, long size) throws FileNotFoundException {
        this(hash, file.getAbsolutePath(), file.getName(), size, 0, System.currentTimeMillis(), FileStatus.REQUEST);
    }

    public File(java.io.File file, ApplicationLayer layer) throws FileNotFoundException {
        this(null, file.getAbsolutePath(), file.getName(), file.length(), 0, System.currentTimeMillis(), FileStatus.HASHING);
        if (size != 0) {
            ex.execute(() -> {
                hash = Utility.fileHash(file);
                setStatus(FileStatus.REQUEST);
                boolean copy = layer.addFile(this);
                if (!copy) {
                    Message message = new UploadRequestMessage(hash, name, size);
                    layer.addEvent(message, Event.EventType.INNER);
                }
            });
        }
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

    public int getProgress() {
        return (int) (100.0 * block * Utility.BLOCK_SIZE / size);
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
        return true;
    }

    public enum FileStatus {
        REQUEST, TRANSFER, DECLINED, COMPLETE, ERROR, HASHING
    }
}
