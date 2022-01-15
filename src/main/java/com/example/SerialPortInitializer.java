package com.example;

import com.example.exceptions.SerialPortException;
import com.fazecast.jSerialComm.SerialPort;

import java.util.Arrays;

public class SerialPortInitializer implements AutoCloseable {

    private final SerialPort serialPort;

    private byte[] buf = {};

    public SerialPortInitializer(String port) throws InterruptedException {
        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort serialPort : ports) {
            if (serialPort.getSystemPortName().equals(port)) {
                serialPort.setBaudRate(115200);
                serialPort.openPort();
                Thread.sleep(3000);
                clearCache();
                this.serialPort = serialPort;
                return;
            }
        }
        throw new SerialPortException("Can't find port: " + port);
    }

    public void clearCache() {
        byte[] buf = new byte[256];
        int bytesAvailable;
        while ((bytesAvailable = serialPort.bytesAvailable()) > 0) {
            int bytesToRead = Math.min(buf.length, bytesAvailable);
            serialPort.readBytes(buf, bytesToRead);
        }
    }

    public int writeBytes(byte[] buffer, long bytesToWrite) {
        return serialPort.writeBytes(buffer, bytesToWrite);
    }

    public int readBytes(byte[] buffer, long bytesToRead) {
        return serialPort.readBytes(buffer, bytesToRead);
    }

    @Override
    public void close() {
        if (serialPort != null) {
            serialPort.closePort();
        }
    }

    private int findEndLine(byte[] array) {
        for (int i = 0, bufLength = array.length; i < bufLength; i++) {
            byte b = array[i];
            if (b == '\n') {
                return i;
            }
        }
        return -1;
    }

    public String readLine() {
        int pos = findEndLine(buf);
        if (pos != -1) {
            String line = new String(Arrays.copyOfRange(buf, 0, pos));
            buf = Arrays.copyOfRange(buf, pos + 1, buf.length);
            return line;
        }
        byte[] localBuf = new byte[256];
        while (true) {
            int i = serialPort.readBytes(localBuf, localBuf.length);
            if (i > 0) {
                pos = findEndLine(localBuf);
                if (pos != -1) {
                    byte[] line = new byte[buf.length + i];
                    System.arraycopy(buf, 0, line, 0, buf.length);
                    System.arraycopy(localBuf, 0, line, buf.length, pos);
                    String s = new String(line);
                    buf = Arrays.copyOfRange(localBuf, pos + 1, localBuf.length);
                    return s;
                }
                byte[] tempBuf = new byte[buf.length + localBuf.length];
                System.arraycopy(buf, 0, tempBuf, 0, buf.length);
                System.arraycopy(localBuf, 0, tempBuf, buf.length, localBuf.length);
                buf = tempBuf;
            }
        }
    }
}
