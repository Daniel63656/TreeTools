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
    private final long id;

    public CommitId() {
        this.id = currentCommitId;
        currentCommitId++;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CommitId)) {
            return false;
        }
        CommitId other = (CommitId) o;
        return this.id == other.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public int compareTo(@NotNull CommitId right) {
        return Long.compare(id, right.id);
    }

    @Override
    public String toString() {
        return Long.toString(id);
    }
}