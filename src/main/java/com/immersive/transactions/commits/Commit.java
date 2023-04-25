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


    protected Commit() {
        commitId = null;
        this.creationRecords = new HashMap<>();
        this.deletionRecords = new HashMap<>();
        this.changeRecords = new DualHashBidiMap<>();
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
        ObjectState newKey = repository.getRemote().createObjectState(te, commitId);
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
        //remove old state from remote first to enable the creation of a new state for this object
        repository.getRemote().removeValue(dme);
        ObjectState after = repository.getRemote().createObjectState(dme, commitId);
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

    /**
     * @return an {@link ObjectState} that is valid before this commit.
     */
    public ObjectState traceBack(ObjectState state) {
        if (changeRecords.containsValue(state))
            state = changeRecords.getKey(state);
        return state;
    }

    /**
     * @return an {@link ObjectState} that is valid after this commit.
     */
    public ObjectState traceForward(ObjectState state) {
        if (changeRecords.containsKey(state))
            state = changeRecords.get(state);
        return state;
    }

    @Override
    public String toString() {
        StringBuilder strb = new StringBuilder();
        if (commitId != null) {
            strb.append("commit number ").append(commitId).append(":\n");
            //use getter so inverted commit is printed correctly
            for (Map.Entry<ObjectState, Object[]> entry : getDeletionRecords().entrySet()) {
                strb.append(">Delete ");
                printState(strb, entry.getKey(), commitId.getPredecessor());
            }
            for (Map.Entry<ObjectState, Object[]> entry : getCreationRecords().entrySet()) {
                strb.append(">Create ");
                printState(strb, entry.getKey(), commitId);
            }
            for (Map.Entry<ObjectState, ObjectState> entry : getChangeRecords().entrySet()) {
                strb.append(">Change ");
                printState(strb, entry.getKey(), commitId.getPredecessor());
                strb.append("     to ");
                printState(strb, entry.getValue(), commitId);
            }
            return strb.append("====================================").toString();
        }
        return "untracked commit";
    }

    private void printState(StringBuilder strb, ObjectState state, CommitId commitId) {
        state.printClassAndId(strb).append(" = {");
        state.printImmutableFields(strb);
        state.printCrossReferences(strb, commitId);
        if (strb.charAt(strb.length()-1) == ' ')
            strb.setLength(strb.length() - 1);
        strb.append("}\n");
    }
}