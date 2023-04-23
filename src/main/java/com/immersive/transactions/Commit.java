package com.immersive.transactions;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import com.immersive.transactions.Remote.ObjectState;

import java.util.HashMap;
import java.util.Map;

/**
 * A class that resembles a specific commit. Holds corresponding deltas since the last commit.
 */
class Commit {

    /**
     * key used to identify the commit
     */
    CommitId commitId;

    /**
     * keep track of deleted objects since the last commit, stored as a pair of an objects' state and corresponding states of the
     * construction parameters (immutable objects act as their own state, {@link MutableObject}s are saved as
     * {@link ObjectState}). These params might be used to instantiate the object when reverting
     */
    Map<ObjectState, Object[]> deletionRecords = new HashMap<>();

    /**
     * keep track of created objects since the last commit, stored as a pair of an objects' state and corresponding states of the
     * construction parameters (immutable objects act as their own key, {@link MutableObject}s are saved as
     * {@link ObjectState}). These params are used to create the object when pulling
     */
    Map<ObjectState, Object[]> creationRecords = new HashMap<>();

    /**
     * keep track of changed (but not created) objects since the last commit. Stored as a pair of their old and
     * new {@link ObjectState}
     */
    DualHashBidiMap<ObjectState, ObjectState> changeRecords = new DualHashBidiMap<>();


    Commit(CommitId commitId) {
        this.commitId = commitId;
    }

    boolean isEmpty() {
        return (deletionRecords.isEmpty() && creationRecords.isEmpty() && changeRecords.isEmpty());
    }

    @Override
    public String toString() {
        StringBuilder strb = new StringBuilder();
        if (commitId != null)
            strb.append("commit number ").append(commitId.id).append(":\n");
        else strb.append("initialization commit:\n");
        for (Map.Entry<ObjectState, Object[]> entry : deletionRecords.entrySet()) {
            strb.append(">Delete ").append(entry.getKey()).append("\n");
        }
        for (Map.Entry<ObjectState, Object[]> entry : creationRecords.entrySet()) {
            strb.append(">Create ").append(entry.getKey()).append("\n");
        }
        for (Map.Entry<ObjectState, ObjectState> entry : changeRecords.entrySet()) {
            strb.append(">Change ").append(entry.getKey()).append("\n     to ").append(entry.getValue()).append("\n");
        }
        return strb.append("====================================").toString();
    }
}
