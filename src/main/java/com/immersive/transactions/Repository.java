package com.immersive.transactions;

import java.util.HashSet;
import java.util.Set;

/**
 * A class that wraps around the {@link com.immersive.transactions.RootEntity} and provides the necessary
 * structures to make transactions possible.
 */
public class Repository {

    /**
     * the wrapped {@link RootEntity}
     */
    RootEntity rootEntity;

    /** link between data model objects and their states, acting as a remote state the data model
     * can revert to while uncommitted changes exist.*/
    Remote remote;

    /** keep track of at which commit the {@link Repository} is currently at */
    CommitId currentCommitId;

    /**
     * used to suppress the creation of deltas during pull
     */
    boolean ongoingPull;

    private final Set<ChildEntity<?>> locallyCreated = new HashSet<>();

    private final Set<ChildEntity<?>> locallyDeleted = new HashSet<>();

    private final Set<MutableObject> locallyChanged = new HashSet<>();


    Repository(RootEntity rootEntity, Remote remote, CommitId currentCommitId) {
        this.rootEntity = rootEntity;
        this.remote = remote;
        this.currentCommitId = currentCommitId;
    }

    boolean hasNoLocalChanges() {
        return locallyCreated.isEmpty() && locallyChanged.isEmpty() && locallyDeleted.isEmpty();
    }

    void logLocalCreation(ChildEntity<?> te) {
        locallyCreated.add(te);
    }

    void logLocalDeletion(ChildEntity<?> te) {
        if (locallyCreated.contains(te)) {
            locallyCreated.remove(te);
        }
        else if (locallyChanged.contains(te)) {
            locallyChanged.remove(te);
            locallyDeleted.add(te);
        }
        else {
            locallyDeleted.add(te);
        }
    }

    //used by aspect
    void logLocalChange(MutableObject dme) {
        if (dme instanceof ChildEntity) {
            if (!locallyCreated.contains(dme) && !locallyDeleted.contains(dme)) {
                locallyChanged.add(dme);
            }
        }
        else locallyChanged.add(dme);
    }

    boolean locallyCreatedContains(ChildEntity<?> te) {
        return locallyCreated.contains(te);
    }

    boolean locallyDeletedContains(ChildEntity<?> te) {
        return locallyDeleted.contains(te);
    }

    boolean locallyChangedContains(MutableObject dme) {
        return locallyChanged.contains(dme);
    }


    ChildEntity<?> getOneCreation() {
        if (locallyCreated.isEmpty())
            return null;
        return locallyCreated.iterator().next();
    }

    ChildEntity<?> getOneDeletion() {
        if (locallyDeleted.isEmpty())
            return null;
        return locallyDeleted.iterator().next();
    }

    MutableObject getOneChange() {
        if (locallyChanged.isEmpty())
            return null;
        return locallyChanged.iterator().next();
    }

    void removeCreation(ChildEntity<?> te) {
        locallyCreated.remove(te);
    }

    void removeDeletion(ChildEntity<?> te) {
        locallyDeleted.remove(te);
    }

    void removeChange(MutableObject dme) {
        locallyChanged.remove(dme);
    }


    void clearUncommittedChanges() {
        locallyCreated.clear();
        locallyDeleted.clear();
        locallyChanged.clear();
    }
}