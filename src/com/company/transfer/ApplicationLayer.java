package com.company.transfer;

import com.company.transfer.interfaces.IApplicationLayer;
import com.company.transfer.interfaces.ILinkLayer;
import com.company.transfer.message.Message;
import com.company.transfer.message.UploadRequestMessage;
import com.company.transfer.message.UploadResponseMessage;
import com.company.transfer.utility.Event;
import com.company.transfer.utility.File;
import com.company.transfer.utility.Hash;
import com.company.transfer.utility.Utility;

import javax.swing.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class ApplicationLayer implements IApplicationLayer {

    private final HashMap<Hash, File> fileMap;
    private final ArrayList<File> fileList;
    private final ArrayList<File> stagedFiles = new ArrayList<>();
    private ArrayList<File> uploadFiles = new ArrayList<>();
    private ArrayList<File> downloadFiles = new ArrayList<>();
    private Model model = new Model(this);

    private Lock eventLock = new ReentrantLock();
    private Condition eventCondition = eventLock.newCondition();

    private Lock uploadLock = new ReentrantLock();
    private Condition uploadCondition = uploadLock.newCondition();

    private MainWindow window;

    private ILinkLayer linkLayer;
    private PriorityBlockingQueue<Event<?>> events = new PriorityBlockingQueue<>();

    public ApplicationLayer(ILinkLayer linkLayer, String config) {
        this.linkLayer = linkLayer;
        fileMap = Utility.getFiles(config);
        fileList = new ArrayList<>(fileMap.values());
    }

    public void setWindow(MainWindow window) {
        this.window = window;
    }

    @Override
    public void receive_msg(byte[] message, int length) {
        Message m = Message.parse(message, length);
        addEvent(m, Event.EventType.OUTER);
    }

    @Override
    public void error_appl() {
        addEvent(new IOException("Error from link layer"), Event.EventType.IO);
    }

    @Override
    public void start_appl() {
        new Thread(() -> {
            while (true) {
                Event<?> event = events.poll();
                if (event == null) {
                    eventLock.lock();
                    try {
                        eventCondition.await();
                    } catch (InterruptedException e) {
                        return;
                    } finally {
                        eventLock.unlock();
                    }
                } else if (event.time - System.currentTimeMillis() > 0) {
                    events.add(event);
                    eventLock.lock();
                    try {
                        eventCondition.await(event.time - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        return;
                    } finally {
                        eventLock.unlock();
                    }
                } else {
                    processEvent(event);
                }

            }
        }, "Event Handler").start();
        new Thread(() -> {
            byte[] buffer;
            while (true) {
                uploadLock.lock();
                try {
                    boolean t = false;
                    for (File file : uploadFiles) {
                        if (file.isError() || !file.readyToTransfer()) {
                            continue;
                        }
                        t = true;
                        int read;
                        buffer = new byte[Utility.BLOCK_SIZE];
                        try {
                            read = file.read(buffer);
                        } catch (IOException e) {
                            addEvent(file, Event.EventType.NOT_FOUND);
                            continue;
                        }
                        if (read == -1) {
                            file.setError();
                            Message message = new Message(file.hash, Message.MessageType.COMPLETE, file.getBlock(), null);
                            addEvent(message, Event.EventType.INNER);
                        } else {
                            byte[] temp_buffer;
                            if (read != buffer.length) {
                                temp_buffer = new byte[read];
                                System.arraycopy(buffer, 0, temp_buffer, 0, read);
                            } else {
                                temp_buffer = buffer;
                            }

                            Message message = new Message(file.hash, Message.MessageType.DATA, file.getBlock(), temp_buffer);
                            try {
                                linkLayer.send_msg(message.toByte());
                                file.incBlock();
                            } catch (IOException e) {
                                addEvent(e, Event.EventType.IO);
                            }
                        }
                    }
                    if (!t) {
                        uploadCondition.await();
                    }
                } catch (InterruptedException e) {

                } finally {
                    uploadLock.unlock();
                }
            }
        }, "Upload Thread").start();
    }

    public void open(java.io.File selectedFile) {
        File file = null;
        try {
            file = new File(selectedFile, this);
        } catch (FileNotFoundException e) {

        }
        if (file == null || file.size == 0) {
            Utility.showError("Empty file.", window);
        } else {
            stagedFiles.add(file);
        }
    }

    public <T> void addEvent(T data, Event.EventType type) {
        events.add(new Event<>(data, type));
        eventLock.lock();
        try {
            eventCondition.signal();
        } finally {
            eventLock.unlock();
        }
    }


    private void deleteFromUpload(File file) {
        uploadLock.lock();
        try {
            uploadFiles.remove(file);
        } finally {
            uploadLock.unlock();
        }
    }

    private void processEvent(Event event) {
        if (event.type == Event.EventType.OUTER || event.type == Event.EventType.INNER) {
            Message m = (Message) event.data;
            File file = fileMap.get(m.hash);
            if (event.type == Event.EventType.INNER) {
                try {
                    linkLayer.send_msg(m.toByte());
                } catch (IOException e) {
                    addEvent(e, Event.EventType.IO);
                }
            } else { // outer messages
                switch (m.type) {
                    case DATA:
                        if (downloadFiles.contains(file)) {
                            if (m.block != file.getBlock()) {
                                addEvent(new IOException("Block number conflict: " + m.block + " , " + file.getBlock()), Event.EventType.IO);
                            }
                            try {
                                file.write(m.data);
                                file.incBlock();
                            } catch (IOException e) {
                                addEvent(e, Event.EventType.IO);
                            }
                        }
                        break;
                    case COMPLETE:
                        uploadLock.lock();
                        try {
                            if (uploadFiles.contains(file)) {
                                file.setStatus(File.FileStatus.COMPLETE);
                                uploadFiles.remove(file);
                                Utility.showError("Success.", window);
                            }
                        } finally {
                            uploadLock.unlock();
                        }
                        if (downloadFiles.contains(file)) {
                            try {
                                file.close();
                            } catch (IOException e) {
                                addEvent(e, Event.EventType.IO);
                            }
                            if (file.hash.equals(Utility.fileHash(new java.io.File(file.path), null))) {
                                Utility.showError("Success.", window);
                                file.setStatus(File.FileStatus.COMPLETE);
                                addEvent(new Message(m.hash, Message.MessageType.COMPLETE, file.getBlock(), null), Event.EventType.INNER);
                                System.out.println("Success");
                            } else {
                                Utility.showError("Error.", window);
                                file.setStatus(File.FileStatus.PAUSE);
                                addEvent(new Message(m.hash, Message.MessageType.ERROR, file.getBlock(), null), Event.EventType.INNER);
                                System.out.println("Error");
                            }
                        }
                        System.gc();
                        break;
                    case ERROR:
                        file.setStatus(File.FileStatus.PAUSE);
                        break;
                    case UPLOAD_REQUEST:
                        UploadRequestMessage upm = (UploadRequestMessage) m;
                        window.showUploadDialog(upm.hash, upm.name, upm.size, fileMap.containsKey(upm.hash));
                        break;
                    case UPLOAD_RESPONSE:
                        UploadResponseMessage upm1 = (UploadResponseMessage) m;
                        if (upm1.accept) {
                            uploadLock.lock();
                            try {
                                file.setStatus(File.FileStatus.TRANSFER);
                                file.resetInputStream(upm1.block);
                                uploadFiles.add(file);
                                uploadCondition.signal();
                            } finally {
                                uploadLock.unlock();
                            }
                        } else {
                            file.setStatus(File.FileStatus.DECLINED);
                        }
                        break;
                    case DOWNLOAD_REQUEST:
                        break;
                    case DOWNLOAD_RESPONSE:
                        break;
                    case STOP_TRANSFER:
                        uploadLock.lock();
                        try {
                            if (uploadFiles.contains(file)) {
                                file.setStatus(File.FileStatus.PAUSE);
                                uploadFiles.remove(file);
                            }
                        } finally {
                            uploadLock.unlock();
                        }
                        if (downloadFiles.contains(file)) {
                            file.setStatus(File.FileStatus.PAUSE);
                            try {
                                file.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            downloadFiles.remove(file);
                        }
                        break;
                }
            }

        } else if (event.type == Event.EventType.IO) {
            // TODO destroy stack, show error
            Exception e = (Exception) event.data;
            System.err.println(e.getMessage());
        } else if (event.type == Event.EventType.NOT_FOUND) {
            deleteFromUpload((File) event.data);
        }
    }

    public ListModel<File> getModel() {
        return model;
    }

    public void accept(Hash hash, String name, long size, boolean b, boolean second) {
        File file;
        if (b) {
            if (!second) {
                try {
                    java.io.File f = new java.io.File(name);
                    if (!f.exists()) {
                        f.createNewFile();
                    }
                    file = new File(hash, f, size);
                } catch (IOException e) {
                    System.err.println();
                    return;
                }
                fileList.add(file);
                fileMap.put(file.hash, file);
            } else {
                file = fileMap.get(hash);
            }
            file.setStatus(File.FileStatus.TRANSFER);
            downloadFiles.add(file);
            addEvent(new UploadResponseMessage(hash, file.getBlock(), true), Event.EventType.INNER);
            return;
        }
        addEvent(new UploadResponseMessage(hash, false), Event.EventType.INNER);
    }

    public boolean addFile(File file) {
        boolean result = fileMap.containsKey(file.hash);
        if (result) {
            Utility.showError(String.format("File %s has already transferred.", file.name), window);
        } else {
            fileMap.put(file.hash, file);
            fileList.add(file);
        }
        stagedFiles.remove(file);
        return result;
    }

    public void delete(File selected) {
        if (selected == null) return;
        stop(selected);
        fileList.remove(selected);
        fileMap.remove(selected.hash);
    }

    public void stop(File selected) {
        if (selected == null) return;
        uploadLock.lock();
        try {
            if (uploadFiles.contains(selected)) {
                Message message = new Message(selected.hash, Message.MessageType.STOP_TRANSFER, 0, null);
                addEvent(message, Event.EventType.INNER);
                uploadFiles.remove(selected);
            }
        } finally {
            uploadLock.unlock();
        }
        if (downloadFiles.contains(selected)) {
            Message message = new Message(selected.hash, Message.MessageType.STOP_TRANSFER, 0, null);
            addEvent(message, Event.EventType.INNER);
            downloadFiles.remove(selected);
        }
        selected.setStatus(File.FileStatus.PAUSE);
    }

    public void start(File selected) {
        if (selected == null) return;
        if (selected.location == File.Location.LOCAL) {
            uploadLock.lock();
            try {
                Message message = new UploadRequestMessage(selected.hash, selected.name, selected.size);
                addEvent(message, Event.EventType.INNER);
            } finally {
                uploadLock.unlock();
            }
        } else {
            assert 1 == 2;
            Message message = new Message(selected.hash, Message.MessageType.DOWNLOAD_REQUEST, selected.getBlock(), null);
            addEvent(message, Event.EventType.INNER);
        }
    }

    private static class Model extends AbstractListModel<File> {
        private final ApplicationLayer layer;

        Model(ApplicationLayer layer) {
            this.layer = layer;
            Executors.newFixedThreadPool(1).execute(() -> {
                try {
                    while (true) {
                        fireContentsChanged(this, 0, getSize());
                        Thread.sleep(500);
                    }
                } catch (InterruptedException e) {

                }
            });
        }

        @Override
        public int getSize() {
            if (layer.fileList != null && layer.stagedFiles != null) {
                return layer.fileList.size() + layer.stagedFiles.size();
            } else {
                return 0;
            }
        }

        @Override
        public File getElementAt(int index) {
            if (index < layer.fileList.size()) {
                return layer.fileList.get(index);
            }
            return layer.stagedFiles.get(index - layer.fileList.size());
        }
    }
}
