package com.immersive.transactions;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import com.immersive.transactions.LogicalObjectTree.LogicalObjectKey;

import java.util.HashMap;
import java.util.Map;

/**
 * A class that resembles a specific commit. Holds corresponding deltas since the last commit
 */
class Commit {

    /**
     * key used to identify the commit
     */
    CommitId commitId;

    /**
     * keep track of deleted objects since the last commit, stored as a pair of key and corresponding keys of
     * constructionParams (immutable objects act as their own key, {@link DataModelEntity}s are saved as
     * {@link LogicalObjectKey}). These params might be used to instantiate the object when reverting
     */
    Map<LogicalObjectKey, Object[]> deletionRecords = new HashMap<>();

    /**
     * keep track of created objects since the last commit, stored as a pair of key and corresponding keys of
     * constructionParams (immutable objects act as their own key, {@link DataModelEntity}s are saved as
     * {@link LogicalObjectKey}). These params are used to create the object when pulling
     */
    Map<LogicalObjectKey, Object[]> creationRecords = new HashMap<>();

    /**
     * keep track of changed (but not created) objects since the last commit. Stored as a pair of their old and
     * new {@link com.immersive.transactions.LogicalObjectTree.LogicalObjectKey}
     */
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
            strb.append(">Created ").append(entry.getKey()).append("\n");
        }
        for (Map.Entry<LogicalObjectKey, LogicalObjectKey> entry : changeRecords.entrySet()) {
            strb.append(">Changed ").append(entry.getKey()).append("\n      to ").append(entry.getValue()).append("\n");
        }
        return strb.append("\n").toString();
    }
}
