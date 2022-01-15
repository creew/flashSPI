package com.example;

import com.example.exceptions.FIleException;
import com.example.exceptions.SerialPortException;
import com.fazecast.jSerialComm.SerialPort;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class Main {

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("java -jar FlashSPI.jar list - show list serial ports");
        System.out.println("java -jar FlashSPI.jar w port file - write file 'file' to port 'port'");
        System.out.println("java -jar FlashSPI.jar r port file size - read flash size 'size' from port 'port to file 'file'");
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        try {
            processArguments(args);
        } catch (SerialPortException | FIleException e) {
            System.out.println("Error: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            printUsage();
        }
    }

    private static void processArguments(String[] args) throws IOException, InterruptedException {
        if (args.length < 1) {
            throw new IllegalArgumentException("No arguments passed");
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
                    throw new IllegalArgumentException("Passed less than 3 args");
                }
                write(args[1], args[2]);
                break;
            case "i":
                info(args[1]);
                break;
            case "r":
                if (args.length < 4) {
                    throw new IllegalArgumentException("Passed less than 4 args");
                }
                read(args[1], args[2], args[3]);
                break;
            default:
                throw new IllegalArgumentException("Unknown arg: " + args[0]);
        }
    }

    private static void info(String port) throws InterruptedException {
        try (SerialPortInitializer spi = new SerialPortInitializer(port)) {
            byte[] readStr = ("i" + "\n").getBytes(StandardCharsets.UTF_8);
            spi.writeBytes(readStr, readStr.length);
            String s = spi.readLine();
            System.out.println("line: " + s);
        }
    }

    private static void read(String port, String path, String size) throws IOException, InterruptedException {
        Path file= getPath(path);
        int length = getLength(size);
        int startOffset = 0;
        try (SerialPortInitializer spi = new SerialPortInitializer(port);
             TimerInitializer ti = new TimerInitializer()) {
            for (long i = 0; i < length; i += 4096)
            {
                byte[] buf = new byte[4096];
                int written = 0;
                byte[] readStr = ("r" + (i + startOffset) + "|").getBytes(StandardCharsets.UTF_8);
                spi.writeBytes(readStr, readStr.length);
                while (written < 4096) {
                    if (ti.get() > 30) {
                        throw new SerialPortException("Timer exceeded");
                    }
                    byte[] readBuf = new byte[4096];
                    int read = spi.readBytes(readBuf, readBuf.length);
                    if (read < 0) {
                        throw new SerialPortException("Error reading from serial port");
                    }
                    if (read != 0) {
                        System.arraycopy(readBuf, 0, buf, written, read);
                        written += read;
                        ti.reset();
                    }
                }
                System.out.println("\nSector " + i / 4096 + ", sz=" + written + ", " + (i * 1.0 / length));
                Files.write(file, buf, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
            }
            System.out.println("Готово!");
        }
    }

    private static int getLength(String size) {
        int length;
        try {
            length = Integer.parseInt(size);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot parse size: " + size);
        }
        if (length <= 0) {
            throw new IllegalArgumentException("Size cannot be less or equal zero");
        }
        return length;
    }

    private static Path getPath(String path) {
        Path file;
        try {
            file = Files.createFile(Paths.get(path));
        } catch (IOException e) {
            throw new FIleException("Cannot create file: " + path + ", error: " + e.getMessage());
        }
        return file;
    }

    private static void write(String port, String path) throws InterruptedException {
        byte[] bytes = readAllBytes(path);
        int sz = bytes.length;
        int needToAdd = 4096 - sz % 4096;
        byte[] bw = new byte[sz + needToAdd];
        System.arraycopy(bytes, 0, bw, 0, sz);
        for (int i = sz; i < sz + needToAdd; i++) {
            bw[i] = (byte) 0xFF;
        }
        int startOffset = 0;
        try (SerialPortInitializer spi = new SerialPortInitializer(port)) {
            for (int i = 0; i < sz; )
            {
                if (i % 4096 == 0)
                {
                    byte[] erase = ("e" + (i + startOffset) + "|").getBytes(StandardCharsets.UTF_8);
                    spi.writeBytes(erase, erase.length);
                    System.out.println("\nErase " + i);
                    String line = spi.readLine();
                    if (!line.startsWith("OK"))
                    {
                        System.out.println("Sect erase " + i  + " error");
                        return;
                    }
                }
                byte[] write = ("w" + (i + startOffset) + "|").getBytes(StandardCharsets.UTF_8);
                spi.writeBytes(write, write.length);
                byte[] data = new byte[128];
                System.arraycopy(bw, i, data, 0, 128);
                spi.writeBytes(data, 128);
                String line = spi.readLine();
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
        }
    }

    private static byte[] readAllBytes(String path) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(Paths.get(path));
        } catch (IOException e) {
            throw new FIleException("Cannot read file: " + path);
        }
        return bytes;
    }
}
