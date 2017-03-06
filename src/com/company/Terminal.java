package com.company;

import java.io.*;
import java.util.Scanner;

public class Terminal {

    private DataInputStream is;
    private DataOutputStream os;

    public Terminal(SerialPortConnector spc, Scanner sc) {
        is = new DataInputStream(spc.getInputStream());
        os = new DataOutputStream(spc.getOutputStream());
        new Thread(() -> {
            try {
                while (true) {
                    System.out.print("> ");
                    String line = sc.nextLine();
                    os.writeUTF(line);
                    os.flush();
                    if (line.equals("exit")) System.exit(0);
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }).start();
        new Thread(() -> {
            try {
                while (true) {
                    String line = is.readUTF();
                    System.out.print("\b\b\b< ");
                    System.out.println(line);
                    System.out.print("> ");
                    if (line.equals("exit")) System.exit(0);
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }).start();
    }


}