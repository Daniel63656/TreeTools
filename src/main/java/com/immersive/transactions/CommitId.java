package com.immersive.transactions;

import org.jetbrains.annotations.NotNull;

public class CommitId implements Comparable<CommitId> {
    int id;

    CommitId(int id) {
        this.id = id;
    }

    @Override
    public int compareTo(@NotNull CommitId right) {
        return Integer.compare(id,right.id);
    }
}
