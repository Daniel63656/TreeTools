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

    @Override
    public String toString() {
        StringBuilder strb = new StringBuilder();
        strb.append("----------Commit number ").append(commitId.id).append(":----------\n");
        for (Map.Entry<LogicalObjectKey, Object[]> entry : deletionRecords.entrySet()) {
            strb.append(">Deleted ").append(entry.getKey()).append("\n");
        }
        for (Map.Entry<LogicalObjectKey, Object[]> entry : creationRecords.entrySet()) {
            strb.append(">Created ").append(entry.getKey()).append(entry.getKey().printSubscribers()).append("\n");
        }
        for (Map.Entry<LogicalObjectKey, LogicalObjectKey> entry : changeRecords.entrySet()) {
            strb.append(">Changed ").append(entry.getKey()).append("\n      to ").append(entry.getValue()).append(entry.getValue().printSubscribers()).append("\n");
        }
        return strb.append("\n").toString();
    }
}
