package com.immersive.transactions;

import java.util.HashSet;
import java.util.Set;

/**
 * A class that wraps around the {@link com.immersive.transactions.RootEntity} and provides the necessary
 * structures to make transactions possible.
 */
public class Workcopy {

    /**
     * the wrapped root entity
     */
    RootEntity rootEntity;
    LogicalObjectTree LOT;
    CommitId currentCommitId;

    /**
     * used by aspect to suppress logging creations during pull
     */
    boolean ongoingPull;


    //deltas
    Set<ChildEntity<?>> locallyDeleted = new HashSet<>();
    Set<DataModelEntity> locallyCreatedOrChanged = new HashSet<>();

    Workcopy(RootEntity rootEntity, LogicalObjectTree LOT, CommitId currentCommitId) {
        this.rootEntity = rootEntity;
        this.LOT = LOT;
        this.currentCommitId = currentCommitId;
    }
}
