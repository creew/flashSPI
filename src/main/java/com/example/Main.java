package com.example;

import com.fazecast.jSerialComm.SerialPort;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Scanner;

import static com.fazecast.jSerialComm.SerialPort.FLOW_CONTROL_DTR_ENABLED;

public class Main {
    private static SerialPort initializePort(String port) {
        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort serialPort : ports) {
            if (serialPort.getSystemPortName().equals(port)) {
                int flowControlSettings = serialPort.getFlowControlSettings();
                flowControlSettings |= FLOW_CONTROL_DTR_ENABLED;
                serialPort.setFlowControl(flowControlSettings);
                serialPort.setBaudRate(115200);
                serialPort.openPort();
                return serialPort;
            }
        }
        return null;
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("java -jar FlashSPI.jar list - show list serial ports");
        System.out.println("java -jar FlashSPI.jar port w file - write file 'file' to port 'port'");
        System.out.println("java -jar FlashSPI.jar port r file size - read flash size 'size' from port 'port to file 'file'");
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 1) {
            printUsage();
        }
        switch (args[0]) {
            case "list":
                SerialPort[] ports = SerialPort.getCommPorts();
                for (SerialPort port : ports) {
                    System.out.println(port.getSystemPortName());
                }
                break;
            case "w":
                if (args.length < 3) {
                    printUsage();
                } else {
                    write(args);
                }
                break;
            case "r":
                if (args.length < 4) {
                    printUsage();
                } else {
                    read(args);
                }
                break;
            default:
                printUsage();
                break;

        }
    }

    private static void read(String[] args) throws IOException, InterruptedException {
        Path file = Files.createFile(Paths.get(args[1]));
        int length = Integer.parseInt(args[2]);
        int startOffset = 0;
        SerialPort s = initializePort(args[0]);
        for (long i = 0; i < length; i += 4096)
        {
            byte[] buf = new byte[4096];
            int readed;
            byte[] read = ("r" + (i + startOffset) + "|").getBytes(StandardCharsets.UTF_8);
            s.writeBytes(read, read.length);
            while (s.bytesAvailable() < 4096)
                Thread.sleep(10);
            readed = s.readBytes(buf, 4096);
            System.out.println("\nSector " + i/4096 + ", sz=" + readed + ", " + (i*1.0/length));
            Files.write(file, buf, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        }
        System.out.println("Готово!");
        s.closePort();
    }

    private static void write(String[] args) throws IOException {
        String ofn = args[1];
        byte[] bytes = Files.readAllBytes(Paths.get(ofn));
        int sz = bytes.length;
        int needToAdd = 4096 - sz % 4096;
        byte[] bw = new byte[sz + needToAdd];
        System.arraycopy(bytes, 0, bw, 0, sz);
        for (int i = sz; i < sz + needToAdd; i++) {
            bw[i] = (byte) 0xFF;
        }
        int startOffset = 0;
        SerialPort s = initializePort(args[0]);
        Scanner scanner = new Scanner(s.getInputStream());
        scanner.useDelimiter("\n");
        for (int i = 0; i < sz; )
        {
            if (i % 4096 == 0)
            {
                byte[] erase = ("e" + (i + startOffset) + "|").getBytes(StandardCharsets.UTF_8);
                s.writeBytes(erase, erase.length);
                System.out.println("\nErase " + i);
                String line = scanner.nextLine();
                if (!line.startsWith("OK"))
                {
                    System.out.println("Sect erase " + i  + " error");
                    return;
                }
            }
            byte[] write = ("w" + (i + startOffset) + "|").getBytes(StandardCharsets.UTF_8);
            s.writeBytes(write, write.length);
            byte[] data = new byte[128];
            System.arraycopy(bw, i, data, 0, 128);
            s.writeBytes(data, 128);
            String line = scanner.nextLine();
            if (!line.startsWith("OK"))
            {
                System.out.println(line);
                System.out.println("Write " + i + " error");
                return;
            }
            System.out.println("\rWrite sector " + (i + startOffset) / 128 + ", sz=128, " + (i * 1.0 / sz) + ": " + line);
            i += 128;
        }
        System.out.println("Готово!");
        s.closePort();
    }
}
