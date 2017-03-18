package com.company.transfer;

import com.company.transfer.interfaces.IApplicationLayer;
import com.company.transfer.interfaces.ILinkLayer;
import com.company.transfer.message.Message;
import com.company.transfer.message.UploadRequestMessage;
import com.company.transfer.message.UploadResponseMessage;
import com.company.transfer.utility.Event;
import com.company.transfer.utility.File;
import com.company.transfer.utility.Utility;

import javax.swing.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class ApplicationLayer implements IApplicationLayer {

    private final HashMap<Message.Hash, File> fileMap;
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
        model.contentChanged();
    }

    @Override
    public void receive_msg(byte[] message) {
        Message m = Message.parse(message);
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
                if (uploadFiles.isEmpty()) {
                    uploadLock.lock();
                    try {
                        uploadCondition.await();
                    } catch (InterruptedException e) {
                        return;
                    } finally {
                        uploadLock.unlock();
                    }
                }
                uploadLock.lock();
                try {
                    for (File file : uploadFiles) {
                        if (file.isError() || !file.readyToTransfer()) {
                            continue;
                        }
                        int read;
                        buffer = new byte[Utility.BLOCK_SIZE];
                        try {
                            read = file.getIs().read(buffer);
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
                            addEvent(message, Event.EventType.INNER);
                            file.incBlock();
                            model.contentChanged();
                        }
                    }
                } finally {
                    uploadLock.unlock();
                }
            }
        }, "Upload Thread").start();
    }

    public void open(java.io.File selectedFile) {
        try {
            File file = new File(selectedFile, this);
            stagedFiles.add(file);
            model.contentChanged();
        } catch (FileNotFoundException e) {

        }
    }

    public <T> void addEvent(T data, Event.EventType type) {
        events.add(new Event<>(data, type));
        model.contentChanged();
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
                    if (m.type == Message.MessageType.DATA) {
                        file.incShowBlock();
                        model.contentChanged();
                    }
                } catch (IOException e) {
                    addEvent(e, Event.EventType.IO);
                }
            } else { // outer message
                switch (m.type) {
                    case DATA:
                        if (downloadFiles.contains(file)) {
                            if (m.block != file.getBlock()) {
                                addEvent(new IOException("Block number conflict: " + m.block + " , " + file.getBlock()), Event.EventType.IO);
                            }
                            try {
                                file.getOs().write(m.data);
                                file.getOs().flush();
                                Message message = new Message(m.hash, Message.MessageType.BLOCK_RECEIVE, file.getBlock(), null);
                                addEvent(message, Event.EventType.INNER);
                                file.incBlock();
                                file.incShowBlock();
                                model.contentChanged();
                            } catch (IOException e) {
                                addEvent(e, Event.EventType.IO);
                            }
                        }
                        break;
                    case COMPLETE:
                        if (uploadFiles.contains(file)) {
                            file.setStatus(File.FileStatus.COMPLETE);
                            uploadFiles.remove(file);
                        } else if (downloadFiles.contains(file)) {
                            try {
                                file.getOs().flush();
                                file.getOs().close();
                            } catch (IOException e) {
                                addEvent(e, Event.EventType.IO);
                            }
                            System.gc();
                            if (Utility.fileHash(new java.io.File(file.path)).equals(file.hash)) {
                                // TODO show complete
                                file.setStatus(File.FileStatus.COMPLETE);
                                addEvent(new Message(m.hash, Message.MessageType.COMPLETE, file.getBlock(), null), Event.EventType.INNER);
                                System.out.println("Success");
                            } else {
                                // TODO show error
                                file.setStatus(File.FileStatus.ERROR);
                                addEvent(new Message(m.hash, Message.MessageType.ERROR, file.getBlock(), null), Event.EventType.INNER);
                                System.out.println("Error");
                            }
                        }
                        model.contentChanged();
                        break;
                    case ERROR:
                        file.setStatus(File.FileStatus.ERROR);
                        model.contentChanged();
                        break;
                    case BLOCK_RECEIVE:
                        break;
                    case UPLOAD_REQUEST:
                        UploadRequestMessage upm = (UploadRequestMessage) m;
                        window.showUploadDialog(upm.hash, upm.name, upm.size);
                        break;
                    case UPLOAD_RESPONSE:
                        UploadResponseMessage upm1 = (UploadResponseMessage) m;
                        if (upm1.accept) {
                            uploadLock.lock();
                            try {
                                file.setStatus(File.FileStatus.TRANSFER);
                                uploadFiles.add(file);
                                uploadCondition.signal();
                            } finally {
                                uploadLock.unlock();
                            }
                        } else {
                            file.setStatus(File.FileStatus.DECLINED);
                        }
                        model.contentChanged();
                        break;
                    case DOWNLOAD_REQUEST:
                        break;
                    case DOWNLOAD_RESPONSE:
                        break;
                    case STOP_TRANSFER:
                        break;
                }
            }

        } else if (event.type == Event.EventType.IO) {
            // TODO destroy stack, show error
            Exception e = (Exception) event.data;
            System.err.println(e);
        } else if (event.type == Event.EventType.NOT_FOUND) {
            deleteFromUpload((File) event.data);
        }
    }

    public ListModel<File> getModel() {
        return model;
    }

    public void accept(Message.Hash hash, String name, boolean b) {
        if (b) {
            try {
                java.io.File f = new java.io.File(name);
                if (!f.exists()) {
                    f.createNewFile();
                }
                File file1 = new File(hash, f);
                file1.setStatus(File.FileStatus.TRANSFER);
                fileList.add(file1);
                fileMap.put(file1.hash, file1);
                model.contentChanged();
                downloadFiles.add(file1);
            } catch (IOException e) {
                System.err.println();
            }
        }
        addEvent(new UploadResponseMessage(hash, b), Event.EventType.INNER);
    }

    public boolean addFile(File file) {
        boolean result = fileMap.containsKey(file.hash);
        if (result) {
            JOptionPane.showMessageDialog(window.getFrame(), "This file has already transferred.");
        } else {
            fileMap.put(file.hash, file);
            fileList.add(file);
        }
        stagedFiles.remove(file);
        model.contentChanged();
        return result;
    }

    private static class Model extends AbstractListModel<File> {
        private ApplicationLayer layer;

        Model(ApplicationLayer layer) {
            this.layer = layer;
        }

        @Override
        public int getSize() {
            return layer.fileList.size() + layer.stagedFiles.size();
        }

        @Override
        public File getElementAt(int index) {
            if (index < layer.fileList.size()) {
                return layer.fileList.get(index);
            }
            return layer.stagedFiles.get(index - layer.fileList.size());
        }

        void contentChanged() {
            fireContentsChanged(this, 0, getSize());
        }
    }
}
