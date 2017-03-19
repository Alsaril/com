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
    public final Location location;
    public Hash hash = null;
    private int block;
    private FileStatus status;
    private InputStream is = null;
    private OutputStream os = null;
    private boolean error = false;

    public File(Hash hash, String path, String name, long size, long date, Location location, int block, FileStatus status) {
        this.hash = hash;
        this.path = path;
        this.name = name;
        this.size = size;
        this.date = date;
        this.location = location;
        this.block = block;
        this.status = status;
    }

    public File(Hash hash, java.io.File file, long size) throws FileNotFoundException {
        this(hash, file.getAbsolutePath(), file.getName(), size, System.currentTimeMillis(), Location.REMOTE, 0, FileStatus.REQUEST);
    }

    public File(java.io.File file, ApplicationLayer layer) throws FileNotFoundException {
        this(null, file.getAbsolutePath(), file.getName(), file.length(), System.currentTimeMillis(), Location.LOCAL, 0, FileStatus.HASHING);
        if (size != 0) {
            ex.execute(() -> {
                hash = Utility.fileHash(file, value -> block = (int) (size * value / Utility.BLOCK_SIZE));
                setStatus(FileStatus.REQUEST);
                block = 0;
                boolean copy = layer.addFile(this);
                if (!copy) {
                    Message message = new UploadRequestMessage(hash, name, size);
                    layer.addEvent(message, Event.EventType.INNER);
                }
            });
        }
    }

    private void initInputStream() {
        assert location == Location.LOCAL;
        if (is == null) {
            try {
                is = new BufferedInputStream(new FileInputStream(path));
            } catch (IOException e) {
                error = true;
            }
        }
    }

    private void initOutputStream() {
        assert location == Location.REMOTE;
        if (os == null) {
            try {
                os = new BufferedOutputStream(new FileOutputStream(path));
            } catch (IOException e) {
                error = true;
            }
        }
    }

    public void write(byte[] data) throws IOException {
        initOutputStream();
        if (os != null) {
            os.write(data);
        }
    }

    public int read(byte[] buffer) throws IOException {
        initInputStream();
        if (is != null) {
            return is.read(buffer);
        }
        throw new IOException();
    }

    public void close() throws IOException {
        if (os != null) {
            os.flush();
            os.close();
        }
    }

    public void resetInputStream(int block) {
        try {
            if (is != null) {
                is.close();
            }
            initInputStream();
            is.skip(block * Utility.BLOCK_SIZE);
        } catch (IOException e) {
            error = true;
        }
    }

    public void resetOutputStream() {
        try {
            if (os != null) {
                os.flush();
                os.close();
            }
            os = new BufferedOutputStream(new FileOutputStream(path, true));
        } catch (IOException e) {
            error = true;
        }
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
        return obj instanceof File && ((File) obj).hash.equals(hash) || obj == this;
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
        HASHING, REQUEST, TRANSFER, DECLINED, COMPLETE, PAUSE
    }

    public enum Location {
        LOCAL, REMOTE
    }
}
