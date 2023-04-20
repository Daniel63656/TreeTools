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

    /** link between data model objects and their content, acting as a remote state the data model
     * can revert to while uncommitted changes exist.*/
    LogicalObjectTree LOT;

    /** keep track of at which commit the workcopy is at */
    CommitId currentCommitId;

    /**
     * used to suppress the creation of deltas during pull
     */
    boolean ongoingPull;

    /** save {@link ChildEntity} deletions. Acts as a delta for uncommitted changes */
    Set<ChildEntity<?>> locallyDeleted = new HashSet<>();

    /** save {@link DataModelEntity} creation or changes. Acts as a delta for uncommitted changes. Creation and
     * changes are combined into one field because created and then changed objects are indistinguishable from
     * objects that were only created */
    Set<DataModelEntity> locallyCreatedOrChanged = new HashSet<>();

    Workcopy(RootEntity rootEntity, LogicalObjectTree LOT, CommitId currentCommitId) {
        this.rootEntity = rootEntity;
        this.LOT = LOT;
        this.currentCommitId = currentCommitId;
    }
}