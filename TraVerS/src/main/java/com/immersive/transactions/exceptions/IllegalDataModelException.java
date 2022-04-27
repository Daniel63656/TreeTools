package com.immersive.transactions.exceptions;


public class IllegalDataModelException extends RuntimeException {
    public IllegalDataModelException(Class<?> clazz, String message) {
        super(clazz.getSimpleName() + " "+message);
    }
}
