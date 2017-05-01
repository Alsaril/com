package com.github.alsaril.application_layer;

import com.github.alsaril.MainWindow;
import com.github.alsaril.application_layer.message.DownloadResponseMessage;
import com.github.alsaril.application_layer.message.Message;
import com.github.alsaril.application_layer.message.UploadRequestMessage;
import com.github.alsaril.application_layer.message.UploadResponseMessage;
import com.github.alsaril.application_layer.utility.Event;
import com.github.alsaril.application_layer.utility.File;
import com.github.alsaril.application_layer.utility.Hash;
import com.github.alsaril.application_layer.utility.Utility;
import com.github.alsaril.interfaces.IApplicationLayer;
import com.github.alsaril.interfaces.ILinkLayer;
import com.github.alsaril.link_layer.LinkLayer;

import javax.swing.*;
import java.io.EOFException;
import java.io.IOException;
import java.util.*;
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
    private List<File> downloadFiles = Collections.synchronizedList(new ArrayList<>());
    private Model model = new Model(this);

    private Lock eventLock = new ReentrantLock();
    private Condition eventCondition = eventLock.newCondition();

    private Lock uploadLock = new ReentrantLock();
    private Condition uploadCondition = uploadLock.newCondition();

    private MainWindow window;

    private ILinkLayer linkLayer = null;
    private PriorityBlockingQueue<Event<?>> events = new PriorityBlockingQueue<>();

    private ConnectionState state = ConnectionState.DISCONNECTED;

    public ApplicationLayer(String config) {
        fileMap = Utility.getFiles(config);
        fileList = new ArrayList<>(fileMap.values());
        fileList.sort(Comparator.comparingLong(f -> f.date));
    }

    public void setWindow(MainWindow window) {
        this.window = window;
    }

    @Override
    public void receive_msg(byte[] message) {
        Message m = Message.parse(message);
        addEvent(m, Event.EventType.OUTER);
    }

    @Override
    public void error() {
        if (state == ConnectionState.CONNECTED) {
            addEvent(new IOException("Error from link layer"), Event.EventType.IO);
        }
    }

    @Override
    public void init() {
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
            byte[] buffer = new byte[Utility.BLOCK_SIZE];
            while (true) {
                List<File> files;
                uploadLock.lock();
                try {
                    files = new ArrayList<>(uploadFiles);
                } finally {
                    uploadLock.unlock();
                }
                boolean t = false;
                for (File file : files) {
                    if (uploadFiles.isEmpty()) {
                        t = false;
                        break;
                    }
                    if (file.isError()) {
                        continue;
                    }
                    t = true;
                    int read;
                    long position = file.getPosition();
                    try {
                        read = file.read(buffer);
                    } catch (IOException e) {
                        addEvent(file, Event.EventType.NOT_FOUND);
                        continue;
                    }
                    if (read == -1) {
                        file.setError();
                        Message message = new Message(file.hash, Message.MessageType.COMPLETE, position, null);
                        addEvent(message, Event.EventType.INNER);
                    } else {
                        byte[] send_buffer = new byte[read];
                        System.arraycopy(buffer, 0, send_buffer, 0, read);
                        Message message = new Message(file.hash, Message.MessageType.DATA, position, send_buffer);
                        try {
                            if (linkLayer == null) {
                                addEvent(new EOFException(), Event.EventType.IO);
                                stopTransfer();
                                t = false;
                                break;
                            }
                            file.initPart();
                            linkLayer.send_msg(message.toByte());
                            file.incPart(message.data.length);
                        } catch (IOException e) {
                            if (state == ConnectionState.CONNECTED) {
                                addEvent(e, Event.EventType.IO);
                            }
                        }
                    }
                }
                uploadLock.lock();
                try {
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
        File file = new File(selectedFile, this);
        if (file.size == 0) {
            Utility.showMessage("Empty file.", window);
        } else {
            stagedFiles.add(file);
        }
    }

    public void addEvent(Event event) {
        eventLock.lock();
        try {
            events.add(event);
            eventCondition.signal();
        } finally {
            eventLock.unlock();
        }
    }

    public <T> void addEvent(T data, Event.EventType type) {
        addEvent(new Event<>(data, type));
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
                if (linkLayer == null) {
                    addEvent(new EOFException(), Event.EventType.IO);
                    stopTransfer();
                    return;
                }
                try {
                    linkLayer.send_msg(m.toByte());
                } catch (IOException e) {
                    addEvent(e, Event.EventType.IO);
                }
            } else { // outer messages
                switch (m.type) {
                    case DATA:
                        if (!downloadFiles.contains(file)) {
                            break;
                        }
                        if (m.position != file.getPosition()) {
                            addEvent(event);
                            System.err.println("Position conflict: get " + m.position + ", need " + file.getPosition());
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                return;
                            }
                            break;
                        }
                        try {
                            file.initPart();
                            file.write(m.data);
                            file.incPart(m.data.length);
                        } catch (IOException e) {
                            addEvent(e, Event.EventType.IO);
                        }
                        break;
                    case COMPLETE:
                        uploadLock.lock();
                        try {
                            if (uploadFiles.contains(file)) {
                                file.setStatus(File.FileStatus.COMPLETE);
                                uploadFiles.remove(file);
                                Utility.showMessage("Передача файла " + file.name + " завершена.", window);
                            }
                        } finally {
                            uploadLock.unlock();
                        }
                        if (downloadFiles.contains(file)) {
                            if (file.hash.equals(Utility.fileHash(new java.io.File(file.path), null))) {
                                Utility.showMessage("Передача файла " + file.name + " завершена.", window);
                                file.setStatus(File.FileStatus.COMPLETE);
                                addEvent(new Message(m.hash, Message.MessageType.COMPLETE, file.getPosition(), null), Event.EventType.INNER);
                            } else {
                                Utility.showMessage("Файл " + file.name + " передан с ошибкой. Повторите передачу.", window);
                                file.setStatus(File.FileStatus.PAUSE);
                                addEvent(new Message(m.hash, Message.MessageType.ERROR, file.getPosition(), null), Event.EventType.INNER);
                            }
                        }
                        System.gc();
                        break;
                    case ERROR:
                        file.setStatus(File.FileStatus.PAUSE);
                        break;
                    case UPLOAD_REQUEST:
                        UploadRequestMessage urm = (UploadRequestMessage) m;
                        window.showTransferDialog(urm.hash, urm.name, urm.size, fileMap.containsKey(urm.hash), true, 0);
                        break;
                    case UPLOAD_RESPONSE:
                        UploadResponseMessage urm1 = (UploadResponseMessage) m;
                        if (urm1.accept) {
                            uploadLock.lock();
                            try {
                                file.setStatus(File.FileStatus.TRANSFER);
                                file.seek(urm1.position);
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
                        if (file == null) {
                            return;
                        }
                        window.showTransferDialog(m.hash, file.name, file.size, true, false, m.position);
                        break;
                    case DOWNLOAD_RESPONSE:
                        DownloadResponseMessage drm = (DownloadResponseMessage) m;
                        if (drm.accept) {
                            file.setStatus(File.FileStatus.TRANSFER);
                            downloadFiles.add(file);
                        }
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
                            downloadFiles.remove(file);
                        }
                        break;
                }
            }

        } else if (event.type == Event.EventType.IO) {
            // TODO destroy stack, show error
            stopTransfer();
            linkLayer = null;
            events.clear();
            Exception e = (Exception) event.data;
            e.printStackTrace();
            Utility.showMessage("Нет соединения", window);
        } else if (event.type == Event.EventType.NOT_FOUND) {
            deleteFromUpload((File) event.data);
        }
    }

    public void stopTransfer() {
        uploadLock.lock();
        try {
            for (File f : uploadFiles) {
                f.setStatus(File.FileStatus.PAUSE);
            }
            uploadFiles.clear();
        } finally {
            uploadLock.unlock();
        }
        for (File f : downloadFiles) {
            f.setStatus(File.FileStatus.PAUSE);
        }
        downloadFiles.clear();
    }

    public ListModel<File> getModel() {
        return model;
    }

    public void accept(Hash hash, String name, long size, boolean accept, boolean second, boolean upload, long position) {
        if (!accept) {
            addEvent(upload ? new DownloadResponseMessage(hash, false) :
                    new UploadResponseMessage(hash, false), Event.EventType.INNER);
            return;
        }
        File file;
        if (!upload) {
            file = fileMap.get(hash);
            addEvent(new DownloadResponseMessage(hash, true), Event.EventType.INNER);
            file.setStatus(File.FileStatus.TRANSFER);
            file.seek(position);

            uploadLock.lock();
            try {
                uploadFiles.add(file);
                uploadCondition.signal();
            } finally {
                uploadLock.unlock();
            }
            return;
        }
        if (!second) {
            try {
                java.io.File f = new java.io.File(Utility.rootPath + java.io.File.separator + name);
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
        addEvent(new UploadResponseMessage(hash, file.getPosition(), true), Event.EventType.INNER);
    }

    public boolean addFile(File file) {
        boolean result = fileMap.containsKey(file.hash);
        if (result) {
            Utility.showMessage(String.format("File %s has already transferred.", file.name), window);
        } else {
            fileMap.put(file.hash, file);
            fileList.add(file);
        }
        stagedFiles.remove(file);
        return result;
    }

    public void request(File file) {
        Message message = new UploadRequestMessage(file.hash, file.name, file.size);
        addEvent(message, Event.EventType.INNER);
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
            Message message = new Message(selected.hash, Message.MessageType.DOWNLOAD_REQUEST, selected.getPosition(), null);
            addEvent(message, Event.EventType.INNER);
        }
    }

    public void connect(String port) {
        try {
            this.linkLayer = new LinkLayer(port, this);
        } catch (EOFException e) {
            addEvent(e, Event.EventType.IO);
        }
    }

    @Override
    public void stateChanged(ConnectionState state) {
        if (state == ConnectionState.DISCONNECTED && this.state == ConnectionState.CONNECTED) {
            addEvent(new EOFException(), Event.EventType.IO);
        }
        this.state = state;
        if (state == ConnectionState.DISCONNECTED) {
            stopTransfer();
        }
        window.stateChanged(state);
    }

    public void disconnect() {
        stopTransfer();
        events.clear();
        state = ConnectionState.DISCONNECTED;
        if (linkLayer != null) {
            linkLayer.close();
        }
        linkLayer = null;
        window.stateChanged(ConnectionState.DISCONNECTED);
    }

    private static class Model extends AbstractListModel<File> {
        private final ApplicationLayer layer;

        Model(ApplicationLayer layer) {
            this.layer = layer;
            Executors.newFixedThreadPool(1).execute(() -> {
                try {
                    while (!Thread.interrupted()) {
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
