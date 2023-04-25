package com.immersive.transactions;

import com.immersive.transactions.commits.Commit;
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

    private CommitId(int id) {
        this.id = id;
    }

    public CommitId getPredecessor() {
        return new CommitId(id-1);
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