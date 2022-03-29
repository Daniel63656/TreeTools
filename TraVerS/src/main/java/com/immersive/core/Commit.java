package com.immersive.core;

import java.util.HashMap;
import java.util.Map;

class Commit {
  CommitId commitId;
  //<Object, Owner>
  Map<LogicalObjectKey, LogicalObjectKey> deletionRecords = new HashMap<>();
  //<Object, Owner>
  Map<LogicalObjectKey, LogicalObjectKey> creationRecords = new HashMap<>();
  //<before, after>
  Map<LogicalObjectKey, LogicalObjectKey> changeRecords   = new HashMap<>();

  Commit(CommitId commitId) {
    this.commitId = commitId;
  }

  @Override
  public String toString() {
    StringBuilder strb = new StringBuilder();
    strb.append("----------Commit number ").append(commitId.id).append(":----------\n");
    for (Map.Entry<LogicalObjectKey, LogicalObjectKey> entry : deletionRecords.entrySet()) {
      strb.append(">Deleted object\n").append(entry.getKey()).append("\n\n");
    }
    for (Map.Entry<LogicalObjectKey, LogicalObjectKey> entry : creationRecords.entrySet()) {
      strb.append(">Created object\n").append(entry.getKey()).append("\n\n");
    }
    for (Map.Entry<LogicalObjectKey, LogicalObjectKey> entry : changeRecords.entrySet()) {
      strb.append(">Changed object from\n").append(entry.getKey()).append("\nto\n").append(entry.getValue()).append("\n\n");
    }
    return strb.toString();
  }
}
