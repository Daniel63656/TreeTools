package com.immersive.transactions.exceptions;

public class NoTransactionsEnabledException extends RuntimeException {
    public NoTransactionsEnabledException() {
        super("No Transactions enabled for specified RootEntity. Use enableTransactionsForRootEntity() to enable Transactions for your RootEntity first!");
    }
}
