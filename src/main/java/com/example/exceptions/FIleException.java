package com.example.exceptions;

public class FIleException extends RuntimeException {
    public FIleException() {
    }

    public FIleException(String message) {
        super(message);
    }
}
