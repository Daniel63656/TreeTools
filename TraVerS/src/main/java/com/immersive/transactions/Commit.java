package com.immersive.transactions;

import java.util.HashMap;
import java.util.Map;

class Commit {
  CommitId commitId;
  //<ObjectToCreate, ConstructionParams>
  Map<LogicalObjectKey, Object[]> deletionRecords = new HashMap<>();
  Map<LogicalObjectKey, Object[]> creationRecords = new HashMap<>();
  //<before, after>
  Map<LogicalObjectKey, LogicalObjectKey> changeRecords = new HashMap<>();

  Commit(CommitId commitId) {
    this.commitId = commitId;
  }

  @Override
  public String toString() {
    StringBuilder strb = new StringBuilder();
    strb.append("----------Commit number ").append(commitId.id).append(":----------\n");
    for (Map.Entry<LogicalObjectKey, Object[]> entry : deletionRecords.entrySet()) {
      strb.append(">Deleted ").append(entry.getKey().clazz.getSimpleName()).append(" with key ").append(entry.getKey().hashCode()).append("\n");
    }
    for (Map.Entry<LogicalObjectKey, Object[]> entry : creationRecords.entrySet()) {
      strb.append(">Created ").append(entry.getKey().clazz.getSimpleName()).append(" with key ").append(entry.getKey().hashCode()).append("\n    ").append(entry.getKey()).append("\n");
    }
    for (Map.Entry<LogicalObjectKey, LogicalObjectKey> entry : changeRecords.entrySet()) {
      strb.append(">Changed ").append(entry.getKey().clazz.getSimpleName()).append(" from\n    ").append(entry.getKey()).append(", key ").append(entry.getKey().hashCode())
              .append("\n    to\n    ").append(entry.getValue()).append(", key ").append(entry.getValue().hashCode()).append("\n");
    }
    return strb.append("\n").toString();
  }
}
