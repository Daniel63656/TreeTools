/*
 * Copyright (c) 2023 Daniel Maier.
 * Licensed under the MIT License.
 */

package net.scoreworks.treetools.commits;

import net.scoreworks.treetools.*;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.util.*;


/**
 * An immutable class that resembles a specific commit. Holds corresponding deltas compared to the last commit
 */
public class Commit {

    /**
     * key used to identify the commit. Null when this is an untracked commit
     */
    private final CommitId commitId;

    /**
     * Keep track of deleted objects since the last commit, stored as a pair of an objects' state and corresponding states of the
     * construction parameters (immutable objects act as their own state, {@link MutableObject}s are saved as
     * {@link Remote.ObjectState}). These params might be used to instantiate the object when reverting, so deletionRecords must
     * reflect the state BEFORE this commit
     */
    protected final Set<Remote.ObjectState> deletionRecords;

    /**
     * Keep track of created objects since the last commit, stored as a pair of an objects' state and corresponding states of the
     * construction parameters (immutable objects act as their own key, {@link MutableObject}s are saved as
     * {@link Remote.ObjectState}). These params may be used to create the object when pulling, so creationRecords must
     * reflect the state AFTER this commit
     */
    protected final Set<Remote.ObjectState> creationRecords;

    /**
     * Keep track of changed (but not created) objects since the last commit. Stored as a pair of their old and
     * new {@link Remote.ObjectState}
     */
    protected final DualHashBidiMap<Remote.ObjectState, Remote.ObjectState> changeRecords;


    public Commit() {
        commitId = null;
        this.creationRecords = new HashSet<>();
        this.deletionRecords = new HashSet<>();
        this.changeRecords = new DualHashBidiMap<>();
    }

    /**
     * Copy an existing {@link Commit} and give it its own proper id
     */
    public Commit(Commit commit) {
        commitId = new CommitId();
        this.creationRecords = commit.creationRecords;
        this.deletionRecords = commit.deletionRecords;
        this.changeRecords = commit.changeRecords;
    }

    protected Commit(Set<Remote.ObjectState> deletionRecords, Set<Remote.ObjectState> creationRecords, DualHashBidiMap<Remote.ObjectState, Remote.ObjectState> changeRecords) {
        commitId = new CommitId();
        this.deletionRecords = deletionRecords;
        this.creationRecords = creationRecords;
        this.changeRecords = changeRecords;
    }

    /**
     * This is the main constructor used to build a commit from the current uncommitted changes of a {@link Repository}
     * @param repository repository to fetch local changes from
     */
    public Commit(Repository repository) {
        commitId = new CommitId();
        this.creationRecords = new HashSet<>();
        this.deletionRecords = new HashSet<>();
        this.changeRecords = new DualHashBidiMap<>();
        Remote remote = repository.getRemote();

        //create ModificationRecords for DELETED objects
        //this becomes tricky because commits need to be invertible in which case deletionRecords become creationRecords.
        //therefore, a deletionRecords objectState must contain the state BEFORE this commit. In order to achieve this,
        //deletions are handled first (to not include changes in owner/key)

        //REMEMBER that invertability also implies that deletion records are present for all subsequent children so
        //that this commit can be fully reverted. ChildEntities remove() function takes care of that
        List<Child<?>> removeFromRemote = new ArrayList<>();
        Child<?> ch = repository.getOneDeletion();
        while (ch != null) {
            deletionRecords.add(remote.getKey(ch));
            //don't remove from remote yet, because this destroys owner information for possible deletion of children
            removeFromRemote.add(ch);
            repository.removeDeletion(ch);
            ch = repository.getOneDeletion();
        }

        //create ModificationRecords for CREATED objects. This may cause other creation or changes to be handled first
        ch = repository.getOneCreation();
        while (ch != null) {
            commitCreation(repository, ch);
            ch = repository.getOneCreation();
        }

        //create ModificationRecords for remaining CHANGED objects. This may cause other creation or changes to be handled first
        MutableObject mo = repository.getOneChange();
        while (mo != null) {
            commitChange(repository, mo);
            mo = repository.getOneChange();
        }

        //now it is safe to remove all deleted object states from remote
        for (Child<?> t : removeFromRemote) {
            remote.removeValue(t);
        }
    }


    /**
     * Process a local creation into a creationRecord. Makes sure that all {@link Remote.ObjectState}s used either
     * for construction parameters or cross-references, are up-to-date
     */
    private void commitCreation(Repository repository, Child<?> ch) {
        //creationRecord contains states needed to construct this object. These states have to be present in the
        //remote. The method makes sure this is the case by recursively processing these creation or changes first

        //loop over all objects needed for construction. Exclude Immutable objects
        for (Object obj : ch.constructorParameterObjects()) {
            if (obj instanceof MutableObject) {
                MutableObject mo = (MutableObject) obj;
                if (mo instanceof Child<?> && repository.locallyCreatedContains((Child<?>) mo))
                    commitCreation(repository, (Child<?>) mo);
                else if (repository.locallyChangedContains(mo))
                    commitChange(repository, mo);
            }
        }
        //object is not currently present in remote, so generate a NEW state and put it in remote. This will also create
        //states for cross-references if needed. If this objects' state already got created through this mechanism,
        //this state is returned from the remote (and no new one created)
        Remote.ObjectState newKey = repository.getRemote().createObjectState(ch);
        //now its save to get the states of owner/keys from the remote and create the creation record with them
        creationRecords.add(newKey);
        //log of from creation tasks
        repository.removeCreation(ch);
    }


    /**
     * Process a local change into a changeRecord. Makes sure that all {@link Remote.ObjectState}s used in
     * cross-references, are up-to-date
     */
    private void commitChange(Repository repository, MutableObject mo) {
        Remote.ObjectState before = repository.getRemote().getKey(mo);
        //updates the state by creating a new state with the same hashCode, so that other states that point to the
        //before state will access the new after state automatically
        //This will also create states for cross-references if needed
        Remote.ObjectState after = repository.getRemote().updateObjectState(mo, before);
        changeRecords.put(before, after);
        //log of from change tasks
        repository.removeChange(mo);
    }

    /**
     * Build an untracked commit used for initial cloning of a data model by parsing the content of a given {@link RootEntity}
     * recursively into a {@link Remote} and adding the object to the commits' {@link Commit#creationRecords}
     */
    public static Commit buildInitializationCommit(Remote remote, RootEntity rootEntity) {
        Commit commit = new Commit();
        parseMutableObject(remote, commit, rootEntity);
        return commit;
    }
    private static void parseMutableObject(Remote remote, Commit commit, MutableObject mo) {
        if (!(mo instanceof RootEntity))
            commit.creationRecords.add(remote.getKey(mo));
        for (Child<?> child : ClassMetadata.getChildren(mo)) {
            parseMutableObject(remote, commit, child);
        }
    }

    public CommitId getCommitId() {
        return commitId;
    }

    public Set<Remote.ObjectState> getDeletionRecords() {
        return SetUtils.unmodifiableSet(deletionRecords);
    }
    public Set<Remote.ObjectState> getCreationRecords() {
        return SetUtils.unmodifiableSet(creationRecords);
    }
    public Map<Remote.ObjectState, Remote.ObjectState> getChangeRecords() {
        return MapUtils.unmodifiableMap(changeRecords);
    }
    public Map<Remote.ObjectState, Remote.ObjectState> getInvertedChangeRecords() {
        return MapUtils.unmodifiableMap(changeRecords.inverseBidiMap());
    }

    public boolean isEmpty() {
        return (deletionRecords.isEmpty() && creationRecords.isEmpty() && changeRecords.isEmpty());
    }

    public void add(Commit commit) {
        for (Remote.ObjectState creationState : commit.creationRecords) {
            if (creationRecords.contains(creationState) ||
                    deletionRecords.contains(creationState) ||
                    changeRecords.containsValue(creationState))
                throw new RuntimeException("Tried to create an object already present in commit!");
            creationRecords.add(creationState);
        }

        for (Remote.ObjectState deleteState : commit.deletionRecords) {
            //deletion is contained as after key in a change
            if (changeRecords.containsValue(deleteState)) {
                Remote.ObjectState beforeState = changeRecords.getKey(deleteState);
                deletionRecords.add(beforeState);
                changeRecords.removeValue(deleteState);
            }
            //deletion is in creationRecords
            else if (creationRecords.contains(deleteState)) {
                creationRecords.remove(deleteState);
            }
            else if (deletionRecords.contains(deleteState))
                throw new RuntimeException("Tried to delete an object that is already deleted!");
                //not contained so far
            else deletionRecords.add(deleteState);
        }

        for (Map.Entry<Remote.ObjectState, Remote.ObjectState> entry : commit.changeRecords.entrySet()) {
            Remote.ObjectState before = entry.getKey();
            Remote.ObjectState after = entry.getValue();
            //change was considered a creation so far - update creation record
            if (creationRecords.contains(before)) {
                //put overrides existing values but not existing keys which we also want -> remove old entry first
                creationRecords.remove(before);
                creationRecords.add(after);
            }
            else if (deletionRecords.contains(before))
                throw new RuntimeException("Tried to change an object that is already deleted!");
            //change is contained as after key in existing change - update change record
            else if (changeRecords.containsValue(before)) {
                //put overrides existing values but not existing keys which we also want -> remove old entry first
                Remote.ObjectState beforeBefore = changeRecords.getKey(before);
                changeRecords.remove(beforeBefore);
                changeRecords.put(beforeBefore, after);
            }
            //not contained so far
            else
                changeRecords.put(before, entry.getValue());
        }
    }

    @Override
    public String toString() {
        StringBuilder strb = new StringBuilder();
        if (commitId != null)
            strb.append("commit number ").append(commitId).append(":\n");
        else strb.append("untracked commit:\n");
        //use getter so inverted commit is printed correctly
        for (Remote.ObjectState entry : getDeletionRecords())
            strb.append(">Delete ").append(entry).append("\n");
        for (Remote.ObjectState entry : getCreationRecords())
            strb.append(">Create ").append(entry).append("\n");
        for (Map.Entry<Remote.ObjectState, Remote.ObjectState> entry : getChangeRecords().entrySet())
            strb.append(">Change ").append(entry.getKey()).append("\n     to ").append(entry.getValue()).append("\n");
        return strb.append("====================================").toString();
    }
}
