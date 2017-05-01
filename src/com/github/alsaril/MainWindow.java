package com.github.alsaril;

import com.github.alsaril.application_layer.ApplicationLayer;
import com.github.alsaril.application_layer.utility.File;
import com.github.alsaril.application_layer.utility.Hash;
import com.github.alsaril.application_layer.utility.Utility;
import com.github.alsaril.interfaces.ConnectionListener;
import com.github.alsaril.physical_layer.PhysicalLayer;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;


public class MainWindow implements ListSelectionListener, ConnectionListener {
    private static final String[] PORTS = {"COM1", "COM2"};
    private static final int PORT = 53454;
    static ApplicationLayer l = null;

    static {
        PhysicalLayer.getPortNames();
    }

    private JFrame frame;
    private JButton delete;
    private JButton start;
    private JButton stop;
    private JLabel status;
    private ApplicationLayer applicationLayer;
    private File selected = null;
    private String port = null;

    public MainWindow(ApplicationLayer applicationLayer) {
        this.applicationLayer = applicationLayer;
        applicationLayer.setWindow(this);
        frame = new JFrame("File Transfer");
        JPanel panel = new JPanel();
        JButton open = new JButton("Open");
        delete = new JButton("Delete");
        start = new JButton("Start");
        stop = new JButton("Stop");
        status = new JLabel();
        JButton settings1 = new JButton("Settings");
        JButton connect = new JButton("Connect");
        JButton disconnect = new JButton("Disconnect");
        delete.setEnabled(false);
        start.setEnabled(false);
        stop.setEnabled(false);
        panel.add(open);
        panel.add(delete);
        panel.add(start);
        panel.add(stop);
        panel.add(settings1);
        panel.add(connect);
        panel.add(disconnect);
        panel.add(status);
        open.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int returnValue = fileChooser.showOpenDialog(frame);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                applicationLayer.open(fileChooser.getSelectedFile());
            }
        });
        settings1.addActionListener(e -> {
            JDialog settings = new JDialog(frame, "settings", true);

            JPanel sPanel = new JPanel();
            sPanel.setLayout(new BoxLayout(sPanel, BoxLayout.Y_AXIS));
            sPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            java.util.List<String> l = PhysicalLayer.getPortNames();
            String[] ports = new String[l.size()];
            l.toArray(ports);
            JComboBox<String> box = new JComboBox<>(ports);
            sPanel.add(new JLabel("COM port"));
            sPanel.add(box);
            sPanel.add(new JLabel("Download directory"));
            JTextField dir = new JTextField(Utility.rootPath);
            sPanel.add(dir);
            dir.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    JFileChooser fileChooser = new JFileChooser();
                    fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    int returnValue = fileChooser.showOpenDialog(frame);
                    if (returnValue == JFileChooser.APPROVE_OPTION) {
                        Utility.rootPath = fileChooser.getSelectedFile().getPath();
                        dir.setText(Utility.rootPath);
                    }
                }
            });
            JButton b = new JButton("OK");
            sPanel.add(b);
            if (box.getModel().getSize() == 0) {
                Utility.showMessage("Нет портов", MainWindow.this);
                return;
            }
            b.addActionListener(e1 -> {
                port = box.getSelectedItem().toString();
                settings.setVisible(false);
            });
            settings.getContentPane().add(sPanel);
            settings.setLocationRelativeTo(frame);
            settings.pack();
            settings.setVisible(true);
        });

        frame.add(panel, BorderLayout.NORTH);
        frame.addWindowListener(new WindowAdapter() {
                                    @Override
                                    public void windowClosing(WindowEvent e) {
                                        applicationLayer.stopTransfer();
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
            JLabel name = new JLabel((file.location == File.Location.LOCAL ? "▲ " : "▼ ") + file.name);
            int progress = file.getProgress();
            JProgressBar progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(true);
            if (file.getStatus() == File.FileStatus.HASHING) {
                progressBar.setValue(progress);
                progressBar.setString(String.format("Hashing (%d%%)", progress));
            } else {
                progressBar.setValue(progress);
                progressBar.setString(null);
            }
            JLabel hash = new JLabel(file.hash + " (" + file.getStatus().toString() + ")" + file.stat());
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
        applicationLayer.getModel().addListDataListener(new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {

            }

            @Override
            public void intervalRemoved(ListDataEvent e) {

            }

            @Override
            public void contentsChanged(ListDataEvent e) {
                MainWindow.this.valueChanged(new ListSelectionEvent(list, 0, 0, false));
            }
        });
        list.addListSelectionListener(this);

        delete.addActionListener(e -> {
            applicationLayer.delete(selected);
            list.clearSelection();
        });
        start.addActionListener(e -> {
            if (selected != null
                    && (selected.getStatus() == File.FileStatus.REQUEST
                    || selected.getStatus() == File.FileStatus.DECLINED)) {
                applicationLayer.request(selected);
            } else {
                applicationLayer.start(selected);
            }
            int i = list.getSelectedIndex();
            list.clearSelection();
            list.setSelectedIndex(i);
        });
        stop.addActionListener(e -> {
            applicationLayer.stop(selected);
            int i = list.getSelectedIndex();
            list.clearSelection();
            list.setSelectedIndex(i);
        });
        connect.addActionListener(e -> {
            if (port == null) {
                Utility.showMessage("Не выбран COM-порт", MainWindow.this);
            } else {
                applicationLayer.connect(port);
            }
        });
        disconnect.addActionListener(e -> {
            applicationLayer.disconnect();
        });
        frame.add(new JScrollPane(list), BorderLayout.CENTER);
        frame.setPreferredSize(new Dimension(600, 400));
        stateChanged(ConnectionState.DISCONNECTED);
        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Not enough arguments");
            return;
        }
        String arg = args[0];
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
        }
        l = new ApplicationLayer(arg);
        l.init();
        new MainWindow(l);
    }

    public JFrame getFrame() {
        return frame;
    }

    public void showTransferDialog(Hash hash, String name, long size, boolean second, boolean upload, long position) {
        SwingUtilities.invokeLater(() -> {
            String text = second ? String.format("Продолжить %s файла \"%s\"?", upload ? "загрузку" : "передачу", name) :
                    String.format("Загрузить файл \"%s\"?\n Размер: %s", name, Utility.unit(size));
            final JOptionPane optionPane = new JOptionPane(text, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION);

            final JDialog dialog = new JDialog(frame, "Click a button", true);

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
            applicationLayer.accept(hash, name, size, value == JOptionPane.YES_OPTION, second, upload, position);
        });
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        selected = (File) ((JList) e.getSource()).getSelectedValue();
        if (selected == null) {
            delete.setEnabled(false);
            start.setEnabled(false);
            stop.setEnabled(false);
            return;
        }
        delete.setEnabled(true);
        switch (selected.getStatus()) {
            case REQUEST:
                start.setEnabled(true);
                stop.setEnabled(false);
                break;
            case TRANSFER:
                start.setEnabled(false);
                stop.setEnabled(true);
                break;
            case DECLINED:
                start.setEnabled(true);
                stop.setEnabled(false);
                break;
            case COMPLETE:
                start.setEnabled(false);
                stop.setEnabled(false);
                break;
            case PAUSE:
                start.setEnabled(true);
                stop.setEnabled(false);
                break;
            case HASHING:
                start.setEnabled(false);
                start.setEnabled(false);
                break;
        }
    }

    @Override
    public void stateChanged(ConnectionState state) {
        System.out.println("Window: " + state);
        ImageIcon ii = new ImageIcon(state == ConnectionState.DISCONNECTED ? "ic_cancel_white_24px.png" : "ic_check_white_24px.png");
        status.setIcon(ii);
    }
}
