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

    final DualHashBidiMap<ObjectState, ObjectState> chRecords;


    Commit(CommitId commitId, Map<ObjectState, Object[]> creationRecords, Map<ObjectState, Object[]> deletionRecords,
           DualHashBidiMap<ObjectState, ObjectState> changeRecords, DualHashBidiMap<ObjectState, ObjectState> chRecords) {
        this.commitId = commitId;
        this.creationRecords = creationRecords;
        this.deletionRecords = deletionRecords;
        this.changeRecords = changeRecords;
        this.chRecords = chRecords;
    }

    Commit(CommitId commitId) {
        this.commitId = commitId;
        this.creationRecords = new HashMap<>();
        this.deletionRecords = new HashMap<>();
        this.changeRecords = new DualHashBidiMap<>();
        chRecords = changeRecords;
    }

    //ongoing commits
    Commit(DualHashBidiMap<ObjectState, ObjectState> chRecords) {
        this.commitId = null;
        this.creationRecords = new HashMap<>();
        this.deletionRecords = new HashMap<>();
        this.changeRecords = new DualHashBidiMap<>();
        this.chRecords = chRecords;
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
            strb.append(">Delete ").append(entry.getKey().toStringWithUpdatedCrossReferences(commitId)).append("\n");
        }
        for (Map.Entry<ObjectState, Object[]> entry : creationRecords.entrySet()) {
            strb.append(">Create ").append(entry.getKey().toStringWithUpdatedCrossReferences(commitId)).append("\n");
        }
        for (Map.Entry<ObjectState, ObjectState> entry : changeRecords.entrySet()) {
            strb.append(">Change ").append(entry.getKey().toStringWithUpdatedCrossReferences(commitId)).append("\n     to ")
                    .append(entry.getValue().toStringWithUpdatedCrossReferences(commitId)).append("\n");
        }
        return strb.append("====================================").toString();
    }
}
