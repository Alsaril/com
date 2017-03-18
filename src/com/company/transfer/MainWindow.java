package com.company.transfer;

import com.company.transfer.interfaces.ILinkLayer;
import com.company.transfer.utility.File;
import com.company.transfer.utility.Hash;
import com.company.transfer.utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainWindow {
    private static final int PORT = 53454;
    static ApplicationLayer l = null;
    private JFrame frame;
    private JButton open;
    private JButton close;
    private JButton start;
    private JButton stop;
    private JButton settings;
    private JButton connect;
    private ApplicationLayer applicationLayer;

    public MainWindow(ApplicationLayer applicationLayer, String title) {
        this.applicationLayer = applicationLayer;
        applicationLayer.setWindow(this);
        frame = new JFrame(title);
        JPanel panel = new JPanel();
        open = new JButton("Open");
        close = new JButton("Close");
        start = new JButton("Start");
        stop = new JButton("Stop");
        settings = new JButton("Settings");
        connect = new JButton("Connect");
        panel.add(open);
        panel.add(close);
        panel.add(start);
        panel.add(stop);
        panel.add(settings);
        panel.add(connect);
        open.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                int returnValue = fileChooser.showOpenDialog(frame);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    applicationLayer.open(fileChooser.getSelectedFile());
                }
            }
        });
        frame.add(panel, BorderLayout.NORTH);
        frame.addWindowListener(new WindowAdapter() {
                                    @Override
                                    public void windowClosing(WindowEvent e) {
                                        Utility.save();
                                        System.exit(0);
                                    }
                                }
        );
        JList<File> list = new JList<>(applicationLayer.getModel());
        list.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        list.setCellRenderer((list1, file, index, isSelected, cellHasFocus) -> {
            JPanel elemPanel = new JPanel();
            elemPanel.setLayout(new BorderLayout());
            JLabel name = new JLabel(file.path);
            int progress = file.getProgress();
            JProgressBar progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(true);
            if (file.getStatus() == File.FileStatus.HASHING) {
                progressBar.setString("Hashing...");
            } else {
                progressBar.setValue(progress);
                progressBar.setString(null);
            }
            JLabel hash = new JLabel(file.hash + " (" + file.getStatus().toString() + ")");
            elemPanel.add(name, BorderLayout.NORTH);
            elemPanel.add(progressBar, BorderLayout.CENTER);
            elemPanel.add(hash, BorderLayout.SOUTH);
            if (!isSelected) {
                elemPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
                elemPanel.setBackground(Color.WHITE);
            } else {
                elemPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                        BorderFactory.createEmptyBorder(2, 2, 2, 2)));
                elemPanel.setBackground(new Color(197, 225, 245));
            }
            return elemPanel;
        });
        frame.add(new JScrollPane(list), BorderLayout.CENTER);
        frame.setPreferredSize(new Dimension(600, 400));
        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Not enough arguments");
            return;
        }
        String arg = args[0];
        if (arg == null) return;
        if (!(arg.equals("client") || arg.equals("server"))) {
            System.out.println("Invalid arguments");
            return;
        }
        boolean server = arg.equals("server");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
        }
        Socket s;
        ServerSocket ss;
        try {
            if (server) {
                System.out.println("Server");
                ss = new ServerSocket(PORT);
                s = ss.accept();
            } else {
                System.out.println("Client");
                s = new Socket("127.0.0.1", PORT);
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
            return;
        }
        DataInputStream is;
        DataOutputStream os;
        try {
            is = new DataInputStream(s.getInputStream());
            os = new DataOutputStream(s.getOutputStream());
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return;
        }
        l = new ApplicationLayer(new ILinkLayer() {
            @Override
            public void receive_byte(byte b) {

            }

            @Override
            public synchronized void send_msg(byte[] message) throws IOException {
                os.writeInt(message.length);
                os.write(message);
                os.flush();
            }

            @Override
            public void error_lnk() {

            }

            @Override
            public void start_lnk() {

            }
        }, arg);
        new MainWindow(l, arg);
        l.start_appl();
        ExecutorService ex = Executors.newFixedThreadPool(1);
        ex.execute(() -> {
            try {
                byte[] message = new byte[Utility.BLOCK_SIZE * 2];
                while (true) {
                    int length = is.readInt();
                    int pos = 0;
                    while (pos != length) {
                        int read = is.read(message, pos, length - pos);
                        if (read == -1) return;
                        pos += read;
                    }
                    l.receive_msg(message, length);
                }
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        });
    }

    public JFrame getFrame() {
        return frame;
    }

    public void showUploadDialog(Hash hash, String name, long size) {
        SwingUtilities.invokeLater(() -> {
            String[] names = {"B", "KB", "MB", "GB"};
            long _size = size;
            int index = 0;
            while (_size >= 1024) {
                _size >>>= 10;
                index++;
            }
            final JOptionPane optionPane = new JOptionPane(
                    "Загрузить файл\n"
                            + name + "\n"
                            + String.format(Locale.US, "Размер: %d %s", _size, names[index]),
                    JOptionPane.QUESTION_MESSAGE,
                    JOptionPane.YES_NO_OPTION);

            final JDialog dialog = new JDialog(frame,
                    "Click a button",
                    true);

            dialog.setContentPane(optionPane);
            dialog.setDefaultCloseOperation(
                    JDialog.DO_NOTHING_ON_CLOSE);
            optionPane.addPropertyChangeListener(
                    e -> {
                        String prop = e.getPropertyName();
                        if (dialog.isVisible()
                                && (e.getSource() == optionPane)
                                && (prop.equals(JOptionPane.VALUE_PROPERTY))) {
                            dialog.setVisible(false);
                        }
                    });
            dialog.pack();
            dialog.setLocationRelativeTo(frame);
            dialog.setVisible(true);

            int value = (Integer) optionPane.getValue();
            if (value == JOptionPane.YES_OPTION) {
                applicationLayer.accept(hash, name, size, true);
            } else if (value == JOptionPane.NO_OPTION) {
                applicationLayer.accept(hash, name, size, false);
            }
        });
    }
}
