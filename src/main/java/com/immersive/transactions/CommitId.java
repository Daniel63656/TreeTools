package com.immersive.transactions;

import org.jetbrains.annotations.NotNull;


/**
 * Acts like a timestamp. Each instantiation increments the id
 */
public class CommitId implements Comparable<CommitId> {
    private static int currentCommitId;
    static void reset() {
        currentCommitId = 0;
    }
    private final int id;

    public CommitId() {
        this.id = currentCommitId;
        currentCommitId++;
    }

    @Override
    public int compareTo(@NotNull CommitId right) {
        return Integer.compare(id, right.id);
    }

    @Override
    public String toString() {
        return Integer.toString(id);
    }
}