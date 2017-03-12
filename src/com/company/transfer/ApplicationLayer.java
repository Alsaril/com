package com.company.transfer;

import com.company.transfer.interfaces.IApplicationLayer;
import com.company.transfer.interfaces.ILinkLayer;

import javax.swing.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.ListIterator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class ApplicationLayer implements IApplicationLayer {

    private HashMap<String, File> files = File.loadFiles("files.txt");
    private ArrayList<File> uploadFiles = new ArrayList<>();
    private ArrayList<File> downloadFiles = new ArrayList<>();
    private Model model = new Model(this);

    private Lock eventLock = new ReentrantLock();
    private Condition eventCondition = eventLock.newCondition();

    private Lock uploadLock = new ReentrantLock();
    private Condition uploadCondition = uploadLock.newCondition();

    private MainWindow window;

    private ILinkLayer linkLayer;
    private PriorityBlockingQueue<Event<?>> events = new PriorityBlockingQueue<>(100);

    public ApplicationLayer(ILinkLayer linkLayer) {
        this.linkLayer = linkLayer;
    }

    public void setMainWindow(MainWindow window) {
        this.window = window;
    }

    @Override
    public void receive_msg(byte[] message) {
        Message m = new Message(message);
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
                        if (file.isError()) {
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
            File file = new File(selectedFile);
            files.put(file.hash, file);
            model.contentChanged();
            String name = file.name;
            ByteBuffer bb = ByteBuffer.allocate(4 + name.length() * 2 + 8);
            bb.putInt(name.length());
            bb.put(name.getBytes());
            bb.putLong(file.size);
            Message message = new Message(file.hash, Message.MessageType.UPLOAD_REQUEST, 0, bb.array());
            addEvent(message, Event.EventType.INNER);
        } catch (FileNotFoundException e) {

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

    public void save() {

    }

    private void deleteFromUpload(File file) {
        uploadLock.lock();
        try {
            for (ListIterator<File> i = uploadFiles.listIterator(); i.hasNext(); ) {
                File f = i.next();
                if (f.equals(file)) {
                    i.remove();
                }
            }
        } finally {
            uploadLock.unlock();
        }
    }

    private void processEvent(Event event) {
        if (event.type == Event.EventType.OUTER || event.type == Event.EventType.INNER) {
            Message m = (Message) event.data;
            File file = files.get(m.hash);
            if (event.type == Event.EventType.INNER) {
                try {
                    linkLayer.send_msg(m.toByte());
                } catch (IOException e) {
                    addEvent(e, Event.EventType.IO);
                }
                if (m.type == Message.MessageType.COMPLETE) {
                    deleteFromUpload(file);
                } else if (m.type == Message.MessageType.UPLOAD_RESPONSE) {
                    downloadFiles.add(file);
                }
            } else {
                // next lines are outer only
                switch (m.type) {
                    case DATA:
                        if (downloadFiles.contains(file)) {
                            System.out.println("Received " + m.block + ", expected " + file.getBlock());
                            if (m.block != file.getBlock()) {
                                addEvent(new IOException("Block number conflict: " + m.block + " , " + file.getBlock()), Event.EventType.IO);
                            }
                            try {
                                file.getOs().write(m.data);
                                Message message = new Message(m.hash, Message.MessageType.BLOCK_RECEIVE, file.getBlock(), null);
                                addEvent(message, Event.EventType.INNER);
                                file.incBlock();
                                model.contentChanged();
                            } catch (IOException e) {
                                addEvent(e, Event.EventType.IO);
                            }
                        }
                        break;
                    case COMPLETE:
                        try {
                            file.getOs().flush();
                            file.getOs().close();
                        } catch (IOException e) {
                            addEvent(e, Event.EventType.IO);
                        }
                        System.gc();
                        if (Utility.fileHash(new java.io.File(file.path)).equals(file.hash)) {
                            // TODO show complete
                            System.out.println("Success");
                        } else {
                            // TODO show error
                            System.out.println("Error");
                        }
                        break;
                    case BLOCK_RECEIVE:
                        if (file != null) {
                            file.incShowBlock();
                            model.contentChanged();
                        }
                        break;
                    case UPLOAD_REQUEST:
                        window.showUploadDialog(m.hash, "Новый файл", 1231);
                        break;
                    case UPLOAD_RESPONSE:
                        uploadLock.lock();
                        try {
                            uploadFiles.add(files.get(m.hash));
                            uploadCondition.signal();
                            //TODO not ignore!
                        } finally {
                            uploadLock.unlock();
                        }
                        break;
                    case DOWNLOAD_REQUEST:

                        // TODO show modal then UPLOAD_RESPONSE

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

    public void accept(String name, String hash, boolean b) {
        if (b) {
            try {
                java.io.File f = new java.io.File(name);
                if (!f.exists()) {
                    f.createNewFile();
                }
                File file1 = new File(hash, f);
                files.put(file1.hash, file1);
                model.contentChanged();
                downloadFiles.add(file1);
            } catch (IOException e) {
                System.err.println();
            }
        }
        Message m = new Message(hash, Message.MessageType.UPLOAD_RESPONSE, 0, new byte[]{b ? (byte) 1 : 0});
        addEvent(m, Event.EventType.INNER);
    }

    private static class Model extends AbstractListModel<File> {
        private ApplicationLayer layer;

        Model(ApplicationLayer layer) {
            this.layer = layer;
        }

        @Override
        public int getSize() {
            return layer.files.size();
        }

        @Override
        public File getElementAt(int index) {
            return layer.files.values().stream().sorted(Comparator.comparingLong(f -> f.date)).toArray(File[]::new)[index];
        }

        void contentChanged() {
            fireContentsChanged(this, 0, getSize());
        }
    }
}
