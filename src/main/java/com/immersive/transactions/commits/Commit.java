package com.immersive.transactions.commits;

import com.immersive.transactions.*;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import com.immersive.transactions.Remote.ObjectState;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * An immutable class that resembles a specific commit. Holds corresponding deltas since the last commit
 */
public class Commit {

    /**
     * key used to identify the commit. Null when this is an untracked commit
     */
    private final CommitId commitId;

    /**
     * keep track of deleted objects since the last commit, stored as a pair of an objects' state and corresponding states of the
     * construction parameters (immutable objects act as their own state, {@link MutableObject}s are saved as
     * {@link ObjectState}). These params might be used to instantiate the object when reverting, so deletionRecords must
     * reflect the state BEFORE this commit
     */
    protected final Map<ObjectState, Object[]> deletionRecords;

    /**
     * keep track of created objects since the last commit, stored as a pair of an objects' state and corresponding states of the
     * construction parameters (immutable objects act as their own key, {@link MutableObject}s are saved as
     * {@link ObjectState}). These params may be used to create the object when pulling, so creationRecords must
     * reflect the state AFTER this commit
     */
    protected final Map<ObjectState, Object[]> creationRecords;

    /**
     * keep track of changed (but not created) objects since the last commit. Stored as a pair of their old and
     * new {@link ObjectState}
     */
    protected final DualHashBidiMap<ObjectState, ObjectState> changeRecords;


    public Commit() {
        commitId = null;
        this.creationRecords = new HashMap<>();
        this.deletionRecords = new HashMap<>();
        this.changeRecords = new DualHashBidiMap<>();
    }

    /**
     * copy an existing {@link Commit} and give it its own proper id
     */
    public Commit(Commit commit) {
        commitId = new CommitId();
        this.creationRecords = commit.creationRecords;
        this.deletionRecords = commit.deletionRecords;
        this.changeRecords = commit.changeRecords;
    }

    protected Commit(Map<ObjectState, Object[]> deletionRecords, Map<ObjectState, Object[]> creationRecords, DualHashBidiMap<ObjectState, ObjectState> changeRecords) {
        commitId = new CommitId();
        this.deletionRecords = deletionRecords;
        this.creationRecords = creationRecords;
        this.changeRecords = changeRecords;
    }

    /**
     * this is the main constructor used to build a commit from the current uncommitted changes of a {@link Repository}
     * @param repository repository to fetch local changes from
     */
    public Commit(Repository repository) {
        commitId = new CommitId();
        this.creationRecords = new HashMap<>();
        this.deletionRecords = new HashMap<>();
        this.changeRecords = new DualHashBidiMap<>();
        Remote remote = repository.getRemote();

        //create ModificationRecords for DELETED objects
        //this becomes tricky because commits need to invertible in which case deletionRecords become creationRecords.
        //therefore, a deletionRecords objectState must contain the state BEFORE this commit. In order to achieve this,
        //deletions are handled first (to not include changes in owner/key)

        //REMEMBER that invertibility also implies that deletion records are present for all subsequent children so
        //that this commit can be fully reverted. ChildEntities remove() function takes care of that
        List<ChildEntity<?>> removeFromLOT = new ArrayList<>();
        ChildEntity<?> te = repository.getOneDeletion();
        while (te != null) {
            deletionRecords.put(remote.getKey(te), te.constructorParameterStates(remote));
            //don't remove from remote yet, because this destroys owner information for possible deletion of children
            removeFromLOT.add(te);
            repository.removeDeletion(te);
            te = repository.getOneDeletion();
        }

        //create ModificationRecords for CREATED objects. This may cause other creation or changes to be handled first
        te = repository.getOneCreation();
        while (te != null) {
            commitCreation(repository, te);
            te = repository.getOneCreation();
        }

        //create ModificationRecords for remaining CHANGED objects. This may cause other creation or changes to be handled first
        MutableObject dme = repository.getOneChange();
        while (dme != null) {
            commitChange(repository, dme);
            dme = repository.getOneChange();
        }

        //now it is safe to remove all deleted object states from remote
        for (ChildEntity<?> t : removeFromLOT) {
            remote.removeValue(t);
        }
    }


    /**
     * process a local creation into a creationRecord. Makes sure that all {@link ObjectState}s used either
     * for construction parameters or cross-references, are up-to-date
     */
    private ObjectState commitCreation(Repository repository, ChildEntity<?> te) {
        //creationRecord contains states needed to construct this object. These states have to be present in the
        //remote. The method makes sure this is the case by recursively processing these creation or changes first

        //loop over all objects needed for construction. Non MutableObjects are not included
        for (MutableObject dme : te.constructorParameterMutables()) {
            if (dme instanceof ChildEntity<?> && repository.locallyCreatedContains((ChildEntity<?>) dme))
                commitCreation(repository, (ChildEntity<?>) dme);
            else if (repository.locallyChangedContains(dme))
                commitChange(repository, dme);
        }
        //te is not currently present in remote, so generates a NEW state and put it in remote
        ObjectState newKey = repository.getRemote().createObjectState(te);
        //now its save to get the states of owner/keys from the remote and create the creation record with them
        creationRecords.put(newKey, te.constructorParameterStates(repository.getRemote()));
        //log of from creation tasks
        repository.removeCreation(te);

        //the newly created state may reference other states in cross-references, that may become outdated with this commit
        //avoid this by deploying the same strategy as above
        makeSureCrossReferencedStatesAreInRemote(repository, newKey);
        return newKey;
    }


    /**
     * process a local change into a changeRecord. Makes sure that all {@link ObjectState}s used in
     * cross-references, are up-to-date
     */
    private ObjectState commitChange(Repository repository, MutableObject dme) {
        ObjectState before = repository.getRemote().getKey(dme);
        ObjectState after = repository.getRemote().updateObjectState(dme, before);
        changeRecords.put(before, after);
        //log of from change tasks
        repository.removeChange(dme);

        //the newly created state may reference other states in cross-references, that may become outdated with this commit
        makeSureCrossReferencedStatesAreInRemote(repository, after);
        return after;
    }

    private void makeSureCrossReferencedStatesAreInRemote(Repository repository, ObjectState state) {
        for (Map.Entry<Field, ObjectState> crossReference : state.getCrossReferences().entrySet()) {
            //get the object the cross-reference is pointing at
            MutableObject dme = repository.getRemote().get(crossReference.getValue());
            if (dme != null) {
                //update or create object first. If the object points back at this due to the cross-reference being
                //circular, this is already logged of from local creations
                if (dme instanceof ChildEntity<?> && repository.locallyCreatedContains((ChildEntity<?>) dme))
                    crossReference.setValue(commitCreation(repository, (ChildEntity<?>) dme));
                else if (repository.locallyChangedContains(dme))
                    crossReference.setValue(commitChange(repository, dme));
            }
        }
    }

    /**
     * build an untracked commit used for initialization by parsing the content of a given {@link RootEntity} recursively
     * into a {@link Remote} and adding the object to the commits' {@link Commit#creationRecords}
     */
    public static Commit buildInitializationCommit(Remote remote, RootEntity rootEntity) {
        Commit commit = new Commit();
        parseMutableObject(remote, commit, rootEntity);
        return commit;
    }
    private static void parseMutableObject(Remote remote, Commit commit, MutableObject dme) {
        if (!(dme instanceof RootEntity))
            commit.creationRecords.put(remote.getKey(dme), dme.constructorParameterStates(remote));
        ArrayList<ChildEntity<?>> children = DataModelInfo.getChildren(dme);
        for (ChildEntity<?> child : children) {
            parseMutableObject(remote, commit, child);
        }
    }

    public CommitId getCommitId() {
        return commitId;
    }

    public Map<ObjectState, Object[]> getDeletionRecords() {
        return MapUtils.unmodifiableMap(deletionRecords);
    }
    public Map<ObjectState, Object[]> getCreationRecords() {
        return MapUtils.unmodifiableMap(creationRecords);
    }
    public Map<ObjectState, ObjectState> getChangeRecords() {
        return MapUtils.unmodifiableMap(changeRecords);
    }
    public Map<ObjectState, ObjectState> getInvertedChangeRecords() {
        return MapUtils.unmodifiableMap(changeRecords.inverseBidiMap());
    }

    public boolean isEmpty() {
        return (deletionRecords.isEmpty() && creationRecords.isEmpty() && changeRecords.isEmpty());
    }

    public void add(Commit commit) {
        for (Map.Entry<ObjectState, Object[]> entry : commit.creationRecords.entrySet()) {
            ObjectState creationState = entry.getKey();
            if (creationRecords.containsKey(creationState) ||
                    deletionRecords.containsKey(creationState) ||
                    changeRecords.containsValue(creationState))
                throw new RuntimeException("Tried to create an object already present in commit!");
            creationRecords.put(creationState, entry.getValue());
        }

        for (Map.Entry<ObjectState, Object[]> entry : commit.deletionRecords.entrySet()) {
            ObjectState deleteState = entry.getKey();
            //deletion is contained as after key in a change
            if (changeRecords.containsValue(deleteState)) {
                ObjectState beforeState = changeRecords.getKey(deleteState);
                deletionRecords.put(beforeState, entry.getValue());
                changeRecords.removeValue(deleteState);
            }
            //deletion is in creationRecords
            else if (creationRecords.containsKey(deleteState)) {
                creationRecords.remove(deleteState);
            }
            else if (deletionRecords.containsKey(deleteState))
                throw new RuntimeException("Tried to delete an object that is already deleted!");
                //not contained so far
            else deletionRecords.put(deleteState, entry.getValue());
        }

        for (Map.Entry<ObjectState, ObjectState> entry : commit.changeRecords.entrySet()) {
            ObjectState before = entry.getKey();
            ObjectState after = entry.getValue();
            //change was considered a creation so far - update creation record
            if (creationRecords.containsKey(before)) {
                //put overrides existing values but not existing keys which we also want -> remove old entry first
                Object[] constructionParams = creationRecords.get(before);
                creationRecords.remove(before);
                creationRecords.put(after, constructionParams);
            }
            else if (deletionRecords.containsKey(before))
                throw new RuntimeException("Tried to change an object that is already deleted!");
            //change is contained as after key in existing change - update change record
            else if (changeRecords.containsValue(before)) {
                //put overrides existing values but not existing keys which we also want -> remove old entry first
                ObjectState beforeBefore = changeRecords.getKey(before);
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
        for (Map.Entry<ObjectState, Object[]> entry : getDeletionRecords().entrySet())
            strb.append(">Delete ").append(entry.getKey()).append("\n");
        for (Map.Entry<ObjectState, Object[]> entry : getCreationRecords().entrySet())
            strb.append(">Create ").append(entry.getKey()).append("\n");
        for (Map.Entry<ObjectState, ObjectState> entry : getChangeRecords().entrySet())
            strb.append(">Change ").append(entry.getKey()).append("\n     to ").append(entry.getValue()).append("\n");
        return strb.append("====================================").toString();
    }
}