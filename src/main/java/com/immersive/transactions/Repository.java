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


    Repository(RootEntity rootEntity, CommitId currentCommitId) {
        this.rootEntity = rootEntity;
        this.remote = new Remote(rootEntity, currentCommitId);
        this.currentCommitId = currentCommitId;
    }

    public Remote getRemote() {
        return remote;
    }

    public boolean hasNoLocalChanges() {
        return locallyCreated.isEmpty() && locallyChanged.isEmpty() && locallyDeleted.isEmpty();
    }

    public void logLocalCreation(ChildEntity<?> te) {
        //pulls are not allowed to create deltas!
        if (ongoingPull)
            return;
        locallyCreated.add(te);
    }

    public void logLocalDeletion(ChildEntity<?> te) {
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
        if (dme instanceof ChildEntity) {
            if (!locallyCreated.contains(dme) && !locallyDeleted.contains(dme)) {
                locallyChanged.add(dme);
            }
        }
        else locallyChanged.add(dme);
    }

    public boolean locallyCreatedContains(ChildEntity<?> te) {
        return locallyCreated.contains(te);
    }

    boolean locallyDeletedContains(ChildEntity<?> te) {
        return locallyDeleted.contains(te);
    }

    public boolean locallyChangedContains(MutableObject dme) {
        return locallyChanged.contains(dme);
    }


    public ChildEntity<?> getOneCreation() {
        if (locallyCreated.isEmpty())
            return null;
        return locallyCreated.iterator().next();
    }

    public ChildEntity<?> getOneDeletion() {
        if (locallyDeleted.isEmpty())
            return null;
        return locallyDeleted.iterator().next();
    }

    public MutableObject getOneChange() {
        if (locallyChanged.isEmpty())
            return null;
        return locallyChanged.iterator().next();
    }

    public void removeCreation(ChildEntity<?> te) {
        locallyCreated.remove(te);
    }

    public void removeDeletion(ChildEntity<?> te) {
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