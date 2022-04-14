package com.immersive.transactions.exceptions;

public class TransactionException extends RuntimeException {
    public TransactionException(String message, int keyID) {
        super(message + " keyID: "+keyID);
    }
}
