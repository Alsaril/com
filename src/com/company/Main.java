package com.company;

import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        List<String> names = SerialPortConnector.getNames();
        System.out.print("Available ports:");
        for (String s : names) {
            System.out.printf(" \"%s\"", s);
        }
        System.out.println(".");
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter port name: ");
        String name = scanner.nextLine();
        SerialPortConnector spc = SerialPortConnector.open(name);
        if (spc == null) {
            System.out.println("Port not found.");
            return;
        }
        new Terminal(spc, scanner);
    }
}
