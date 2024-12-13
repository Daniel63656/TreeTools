/*
 * Copyright (c) 2023 Daniel Maier.
 * Licensed under the MIT License.
 */

package net.scoreworks.treetools;

import java.util.HashSet;
import java.util.Set;

/**
 * A class that links {@link RootEntity} with a {@link Remote} and provides the necessary
 * structures to make transactions possible.
 */
public class Repository {

    /**
     * The wrapped {@link RootEntity}
     */
    RootEntity rootEntity;

    /** Link between data model objects and their states, acting as a remote state the data model
     * can revert to while uncommitted changes exist.*/
    Remote remote;

    /** Keep track of at which commit the {@link Repository} is currently at */
    CommitId currentCommitId;

    /**
     * Used to suppress the creation of deltas during pull
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

    public void logLocalCreation(Child<?> ch) {
        //pulls are not allowed to create deltas!
        if (ongoingPull)
            return;
        locallyCreated.add(ch);
    }

    public void logLocalDeletion(Child<?> ch) {
        //pulls are not allowed to create deltas!
        if (ongoingPull)
            return;
        if (locallyCreated.contains(ch)) {
            locallyCreated.remove(ch);
        }
        else if (locallyChanged.contains(ch)) {
            locallyChanged.remove(ch);
            locallyDeleted.add(ch);
        }
        else {
            locallyDeleted.add(ch);
        }
    }

    //used by aspect
    public void logLocalChange(MutableObject mo) {
        //pulls are not allowed to create deltas!
        if (ongoingPull)
            return;
        if (mo instanceof Child) {
            if (!locallyCreated.contains(mo) && !locallyDeleted.contains(mo)) {
                locallyChanged.add(mo);
            }
        }
        else locallyChanged.add(mo);
    }

    public boolean locallyCreatedContains(Child<?> ch) {
        return locallyCreated.contains(ch);
    }

    boolean locallyDeletedContains(Child<?> ch) {
        return locallyDeleted.contains(ch);
    }

    public boolean locallyChangedContains(MutableObject mo) {
        return locallyChanged.contains(mo);
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

    public void removeCreation(Child<?> ch) {
        locallyCreated.remove(ch);
    }

    public void removeDeletion(Child<?> ch) {
        locallyDeleted.remove(ch);
    }

    public void removeChange(MutableObject mo) {
        locallyChanged.remove(mo);
    }


    void clearUncommittedChanges() {
        locallyCreated.clear();
        locallyDeleted.clear();
        locallyChanged.clear();
    }
}
