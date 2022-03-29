package com.immersive.core;

import org.jetbrains.annotations.NotNull;

class CommitId implements Comparable<CommitId> {
  int id;

  CommitId(int id) {
    this.id = id;
  }

  @Override
  public int compareTo(@NotNull CommitId right) {
    return Integer.compare(id,right.id);
  }
}
