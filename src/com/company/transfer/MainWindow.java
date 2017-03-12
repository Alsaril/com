package com.company.transfer;

import com.company.transfer.interfaces.ILinkLayer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;


public class MainWindow {
    static ApplicationLayer l1 = null;
    static ApplicationLayer l2 = null;
    private JFrame frame;
    private JButton open;
    private JButton close;
    private JButton start;
    private JButton stop;
    private JButton settings;
    private JButton connect;
    private ApplicationLayer applicationLayer;

    public MainWindow(ApplicationLayer applicationLayer) {
        this.applicationLayer = applicationLayer;
        applicationLayer.setMainWindow(this);
        frame = new JFrame();
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
                                        applicationLayer.save();
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
            progressBar.setValue(progress);
            progressBar.setStringPainted(true);
            JLabel hash = new JLabel(file.hash);
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
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Windows".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
        }
        l1 = new ApplicationLayer(new ILinkLayer() {
            @Override
            public void receive_byte(byte b) {

            }

            @Override
            public synchronized void send_msg(byte[] message) throws IOException {
                l2.receive_msg(message);
            }

            @Override
            public void error_lnk() {

            }

            @Override
            public void start_lnk() {

            }
        });
        l2 = new ApplicationLayer(new ILinkLayer() {
            @Override
            public void receive_byte(byte b) {

            }

            @Override
            public synchronized void send_msg(byte[] message) throws IOException {
                l1.receive_msg(message);
            }

            @Override
            public void error_lnk() {

            }

            @Override
            public void start_lnk() {

            }
        });
        new MainWindow(l1);
        new MainWindow(l2);
        l1.start_appl();
        l2.start_appl();
    }

    public void showUploadDialog(String hash, String name, long size) {
        SwingUtilities.invokeLater(() -> {
            final JOptionPane optionPane = new JOptionPane(
                    "Загрузить файл\n"
                            + name + "\n"
                            + "Размер: " + size + " байт?",
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
            dialog.setVisible(true);

            int value = (Integer) optionPane.getValue();
            if (value == JOptionPane.YES_OPTION) {
                applicationLayer.accept(name, hash, true);
            } else if (value == JOptionPane.NO_OPTION) {
                applicationLayer.accept(name, hash, false);
            }
        });
    }
}
