package com.example.exceptions;

public class SerialPortException extends RuntimeException {
    public SerialPortException() {
    }

    public SerialPortException(String message) {
        super(message);
    }
}
