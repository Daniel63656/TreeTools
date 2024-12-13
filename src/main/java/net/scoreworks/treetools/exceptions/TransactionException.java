/*
 * Copyright (c) 2023 Daniel Maier.
 * Licensed under the MIT License.
 */

package net.scoreworks.treetools.exceptions;

public class TransactionException extends RuntimeException {
    public TransactionException(String message, int keyID) {
        super(message + " with id ["+keyID+"]");
    }
}
