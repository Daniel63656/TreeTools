package com.immersive.transactions.exceptions;

/**
 * An exception that gets thrown if a class of a proposed data model violates the rules needed to make
 * transactions work
 */
public class IllegalDataModelException extends RuntimeException {
    public IllegalDataModelException(Class<?> clazz, String message) {
        super(clazz.getSimpleName() + " "+message);
    }
}
