package com.immersive.transactions;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import com.immersive.transactions.Remote.ObjectState;

import java.util.HashMap;
import java.util.Map;

/**
 * A class that resembles a specific commit. Holds corresponding deltas since the last commit. Supposed to be
 * completely IMMUTABLE
 */
class Commit {

    /**
     * key used to identify the commit
     */
    final CommitId commitId;

    /**
     * keep track of deleted objects since the last commit, stored as a pair of an objects' state and corresponding states of the
     * construction parameters (immutable objects act as their own state, {@link MutableObject}s are saved as
     * {@link ObjectState}). These params might be used to instantiate the object when reverting
     */
    final Map<ObjectState, Object[]> deletionRecords;

    /**
     * keep track of created objects since the last commit, stored as a pair of an objects' state and corresponding states of the
     * construction parameters (immutable objects act as their own key, {@link MutableObject}s are saved as
     * {@link ObjectState}). These params are used to create the object when pulling
     */
    final Map<ObjectState, Object[]> creationRecords;

    /**
     * keep track of changed (but not created) objects since the last commit. Stored as a pair of their old and
     * new {@link ObjectState}
     */
    final DualHashBidiMap<ObjectState, ObjectState> changeRecords;

    Commit(CommitId commitId) {
        this.commitId = commitId;
        this.creationRecords = new HashMap<>();
        this.deletionRecords = new HashMap<>();
        this.changeRecords = new DualHashBidiMap<>();
    }

    protected Commit(CommitId commitId, Map<ObjectState, Object[]> deletionRecords, Map<ObjectState, Object[]> creationRecords, DualHashBidiMap<ObjectState, ObjectState> changeRecords) {
        this.commitId = commitId;
        this.deletionRecords = deletionRecords;
        this.creationRecords = creationRecords;
        this.changeRecords = changeRecords;
    }

    boolean isEmpty() {
        return (deletionRecords.isEmpty() && creationRecords.isEmpty() && changeRecords.isEmpty());
    }

    ObjectState traceBack(ObjectState state) {
        if (changeRecords.containsValue(state))
            state = changeRecords.getKey(state);
        return state;
    }

    ObjectState traceForward(ObjectState state) {
        if (changeRecords.containsKey(state))
            state = changeRecords.get(state);
        return state;
    }

    @Override
    public String toString() {
        StringBuilder strb = new StringBuilder();
        if (commitId != null)
            strb.append("commit number ").append(commitId.id).append(":\n");
        else strb.append("initialization commit:\n");
        for (Map.Entry<ObjectState, Object[]> entry : deletionRecords.entrySet()) {
            strb.append(">Delete ").append(entry.getKey().toString(commitId)).append("\n");
        }
        for (Map.Entry<ObjectState, Object[]> entry : creationRecords.entrySet()) {
            strb.append(">Create ").append(entry.getKey().toString(commitId)).append("\n");
        }
        for (Map.Entry<ObjectState, ObjectState> entry : changeRecords.entrySet()) {
            strb.append(">Change ").append(entry.getKey().toString(commitId)).append("\n     to ")
                    .append(entry.getValue().toString(commitId)).append("\n");
        }
        return strb.append("====================================").toString();
    }
}
