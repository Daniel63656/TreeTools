package com.immersive.transactions.commits;

import org.jetbrains.annotations.NotNull;


public class CommitId implements Comparable<CommitId> {
    int id;

    public CommitId(int id) {
        this.id = id;
    }

    public static CommitId increment(CommitId commitId) {
        return new CommitId(commitId.id+1);
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
