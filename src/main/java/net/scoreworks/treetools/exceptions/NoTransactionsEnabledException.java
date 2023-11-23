package net.scoreworks.treetools.exceptions;

public class NoTransactionsEnabledException extends RuntimeException {
    public NoTransactionsEnabledException() {
        super("No Transactions enabled for specified RootEntity. Use enableTransactionsForRootEntity() to enable Transactions first!");
    }
}