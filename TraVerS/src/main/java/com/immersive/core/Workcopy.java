package com.immersive.core;

import java.util.HashSet;
import java.util.Set;

public class Workcopy {
    boolean ongoingCreation;    //used by aspect to suppress logging changes during construction
    boolean ongoingPull;        //used by aspect to suppress logging creations during pull
    RootEntity rootEntity;
    LogicalObjectTree LOT;
    CommitId currentCommitId;

    Set<ChildEntity<?>> locallyDeleted = new HashSet<>();
    Set<DataModelEntity> locallyChangedOrCreated = new HashSet<>();

    Workcopy(RootEntity rootEntity, LogicalObjectTree LOT, CommitId currentCommitId) {
        this.rootEntity = rootEntity;
        this.LOT = LOT;
        this.currentCommitId = currentCommitId;
    }
}
