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

    private final Set<Child<?>> locallyCreated = new HashSet<>();

    private final Set<Child<?>> locallyDeleted = new HashSet<>();

    private final Set<MutableObject> locallyChanged = new HashSet<>();


    Repository(RootEntity rootEntity, CommitId currentCommitId) {
        this.rootEntity = rootEntity;
        this.remote = new Remote(rootEntity);
        this.currentCommitId = currentCommitId;
    }

    public Remote getRemote() {
        return remote;
    }

    public boolean hasNoLocalChanges() {
        return locallyCreated.isEmpty() && locallyChanged.isEmpty() && locallyDeleted.isEmpty();
    }

    public void logLocalCreation(Child<?> te) {
        //pulls are not allowed to create deltas!
        if (ongoingPull)
            return;
        locallyCreated.add(te);
    }

    public void logLocalDeletion(Child<?> te) {
        //pulls are not allowed to create deltas!
        if (ongoingPull)
            return;
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
    public void logLocalChange(MutableObject dme) {
        //pulls are not allowed to create deltas!
        if (ongoingPull)
            return;
        if (dme instanceof Child) {
            if (!locallyCreated.contains(dme) && !locallyDeleted.contains(dme)) {
                locallyChanged.add(dme);
            }
        }
        else locallyChanged.add(dme);
    }

    public boolean locallyCreatedContains(Child<?> te) {
        return locallyCreated.contains(te);
    }

    boolean locallyDeletedContains(Child<?> te) {
        return locallyDeleted.contains(te);
    }

    public boolean locallyChangedContains(MutableObject dme) {
        return locallyChanged.contains(dme);
    }


    public Child<?> getOneCreation() {
        if (locallyCreated.isEmpty())
            return null;
        return locallyCreated.iterator().next();
    }

    public Child<?> getOneDeletion() {
        if (locallyDeleted.isEmpty())
            return null;
        return locallyDeleted.iterator().next();
    }

    public MutableObject getOneChange() {
        if (locallyChanged.isEmpty())
            return null;
        return locallyChanged.iterator().next();
    }

    public void removeCreation(Child<?> te) {
        locallyCreated.remove(te);
    }

    public void removeDeletion(Child<?> te) {
        locallyDeleted.remove(te);
    }

    public void removeChange(MutableObject dme) {
        locallyChanged.remove(dme);
    }


    void clearUncommittedChanges() {
        locallyCreated.clear();
        locallyDeleted.clear();
        locallyChanged.clear();
    }
}