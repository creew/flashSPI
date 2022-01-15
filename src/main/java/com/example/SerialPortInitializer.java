package com.example;

import com.example.exceptions.SerialPortException;
import com.fazecast.jSerialComm.SerialPort;

import java.io.InputStream;

import static com.fazecast.jSerialComm.SerialPort.FLOW_CONTROL_DTR_ENABLED;

public class SerialPortInitializer implements AutoCloseable {

    private final SerialPort serialPort;

    public SerialPortInitializer(String port) {
        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort serialPort : ports) {
            if (serialPort.getSystemPortName().equals(port)) {
                int flowControlSettings = serialPort.getFlowControlSettings();
                flowControlSettings |= FLOW_CONTROL_DTR_ENABLED;
                serialPort.setFlowControl(flowControlSettings);
                serialPort.setBaudRate(115200);
                serialPort.openPort();
                this.serialPort = serialPort;
                return;
            }
        }
        throw new SerialPortException("Can't find port: " + port);
    }

    public int writeBytes(byte[] buffer, long bytesToWrite) {
        return serialPort.writeBytes(buffer, bytesToWrite);
    }

    public int readBytes(byte[] buffer, long bytesToRead) {
        return serialPort.readBytes(buffer, bytesToRead);
    }

    public InputStream getInputStream() {
        return serialPort.getInputStream();
    }

    @Override
    public void close() {
        if (serialPort != null) {
            serialPort.closePort();
        }
    }
}
