package com.immersive.transactions;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.util.HashMap;
import java.util.Map;

class Commit {
  CommitId commitId;
  //<ObjectToCreate, ConstructionParams>
  Map<LogicalObjectKey, Object[]> deletionRecords = new HashMap<>();
  Map<LogicalObjectKey, Object[]> creationRecords = new HashMap<>();
  //<before, after>
  DualHashBidiMap<LogicalObjectKey, LogicalObjectKey> changeRecords = new DualHashBidiMap<>();

  Commit(CommitId commitId) {
    this.commitId = commitId;
  }

  boolean isEmpty() {
    return (deletionRecords.isEmpty() && creationRecords.isEmpty() && changeRecords.isEmpty());
  }

  void addTo(Commit dstCommit) {
    for (Map.Entry<LogicalObjectKey, Object[]> entry : deletionRecords.entrySet()) {
      if (dstCommit.changeRecords.containsValue(entry.getKey())) {
        dstCommit.deletionRecords.put(dstCommit.changeRecords.getKey(entry.getKey()), entry.getValue());
        dstCommit.changeRecords.removeValue(entry.getKey());
      }
      else {
        if (dstCommit.creationRecords.containsKey(entry.getKey()))
          dstCommit.creationRecords.remove(entry.getKey());
        else
          dstCommit.deletionRecords.put(entry.getKey(), entry.getValue());
      }
    }
    dstCommit.creationRecords.putAll(creationRecords);
    for (Map.Entry<LogicalObjectKey, LogicalObjectKey> entry : changeRecords.entrySet()) {
      if (dstCommit.changeRecords.containsValue(entry.getKey()))
        dstCommit.changeRecords.put(dstCommit.changeRecords.getKey(entry.getKey()), entry.getValue());
      else
        dstCommit.changeRecords.put(entry.getKey(), entry.getValue());
    }
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
