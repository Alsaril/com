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
    public Hash hash = Hash.EMPTY;
    private FileStatus status;
    private RandomAccessFile raf;
    private boolean error = false;
    private double progress;

    public File(Hash hash, String path, String name, long size, long date, Location location, FileStatus status, long position) {
        this.hash = hash;
        this.path = path;
        this.name = name;
        this.size = size;
        this.date = date;
        this.location = location;
        this.status = status;
        try {
            raf = new RandomAccessFile(path, "rw");
            raf.seek(position);
        } catch (IOException e) {

        }
    }

    public File(Hash hash, java.io.File file, long size) throws FileNotFoundException {
        this(hash, file.getAbsolutePath(), file.getName(), size, System.currentTimeMillis(), Location.REMOTE, FileStatus.REQUEST, 0);
    }

    public File(java.io.File file, ApplicationLayer layer) throws FileNotFoundException {
        this(Hash.EMPTY, file.getAbsolutePath(), file.getName(), file.length(), System.currentTimeMillis(), Location.LOCAL, FileStatus.HASHING, 0);
        if (size != 0) {
            ex.execute(() -> {
                hash = Utility.fileHash(file, value -> progress = value);
                setStatus(FileStatus.REQUEST);
                boolean copy = layer.addFile(this);
                if (!copy) {
                    Message message = new UploadRequestMessage(hash, name, size);
                    layer.addEvent(message, Event.EventType.INNER);
                }
            });
        }
    }

    public static File read(DataInputStream dis) throws IOException {
        boolean hasHash = dis.readBoolean();
        Hash hash;
        if (hasHash) {
            byte[] buffer = new byte[Hash.LENGTH];
            dis.readFully(buffer);
            hash = new Hash(buffer);
        } else {
            hash = Hash.EMPTY;
        }
        return new File(hash,
                dis.readUTF(), // path
                dis.readUTF(), // name
                dis.readLong(), // size
                dis.readLong(), // date
                Location.values()[dis.readInt()],
                FileStatus.values()[dis.readInt()],
                dis.readLong()); //position
    }

    public void write(byte[] data) throws IOException {
        raf.write(data);
    }

    public int read(byte[] buffer) throws IOException {
        return raf.read(buffer);
    }

    public void seek(long position) throws IOException {
        raf.seek(position);
    }

    public long getPosition() {
        try {
            return raf.getFilePointer();
        } catch (IOException e) {
            return -1;
        }
    }

    public int getProgress() {
        return status == FileStatus.HASHING ? (int) (100.0 * progress) : (int) (100.0 * getPosition() / size);
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

    public void write(DataOutputStream dos) throws IOException {
        dos.writeBoolean(!hash.isEmpty());
        if (!hash.isEmpty()) {
            dos.write(hash.value());
        }
        dos.writeUTF(path);
        dos.writeUTF(name);
        dos.writeLong(size);
        dos.writeLong(date);
        dos.writeInt(location.ordinal());
        dos.writeInt(status.ordinal());
        dos.writeLong(getPosition());
    }

    public enum FileStatus {
        HASHING, REQUEST, TRANSFER, DECLINED, COMPLETE, PAUSE
    }

    public enum Location {
        LOCAL, REMOTE
    }
}
