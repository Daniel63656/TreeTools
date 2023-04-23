package com.immersive.transactions;

import com.immersive.transactions.annotations.CrossReference;
import com.immersive.transactions.exceptions.NoTransactionsEnabledException;
import com.immersive.transactions.exceptions.TransactionException;
import com.immersive.transactions.Remote.ObjectState;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


/**
 * Class that handles transactions of a data model. Transactions are realized by first
 * enabling transactions on a {@link RootEntity}. This creates an associated {@link Remote} that links each
 * {@link MutableObject} with an immutable {@link ObjectState}. This REMOTE state is therefore not affected by local changes, until it
 * is synchronized by a {@link TransactionManager#commit(RootEntity)}. Likewise, created copies of the data model can receive
 * those changes by calling {@link TransactionManager#pull(RootEntity)}. This will automatically notify any {@link Wrapper} about
 * changes and deletions made to the data model.
 */
public class TransactionManager {

    /**
     * this class is a singleton!
     */
    private static final TransactionManager transactionManager = new TransactionManager();
    private TransactionManager() {}

    public static TransactionManager getInstance() {
        return transactionManager;
    }

    /** keep track of every existing {@link Repository} */
    Map<RootEntity, Repository> repositories = new HashMap<>();

    /** save all commits in a time-ordered manner. Commits document and coordinate changes across all repositories */
    final TreeMap<CommitId, Commit> commits = new TreeMap<>();

    /** object responsible for grouping commits and providing a history for {@link TransactionManager#undo(RootEntity)}
     * and {@link TransactionManager#redo(RootEntity)} */
    History history;

    /**
     * {@link CommitId} for the next commit to come. Each {@link TransactionManager#commit(RootEntity)} will increment this value
     */
    private CommitId currentCommitId = new CommitId(1);

    /** print messages for debug purposes */
    boolean verbose;
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean transactionsEnabled(RootEntity rootEntity) {
        return repositories.containsKey(rootEntity);
    }

    /**
     * enables transactions for a data model. After this method is called once on the data model,
     * new workable copies can be retrieved with {@link TransactionManager#clone(RootEntity)}
     */
    public void enableTransactionsForRootEntity(RootEntity rootEntity) {
        //transactions are enabled, if there exists at least one repository. If repositories is empty, create the
        //first repo for the given rootEntity
        if (repositories.isEmpty()) {
            Remote LOT = new Remote();
            CommitId  initializationId = new CommitId(0);
            Repository repository = new Repository(rootEntity, LOT, initializationId);
            buildLogicalObjectTree(LOT, rootEntity, initializationId);
            repositories.put(rootEntity, repository);
        }
    }

    public void enableUndoRedos(int capacity) {
        history = new History(capacity);
    }

    /**
     * @param rootEntity root of the data model that is to be copied. Needs to have transactions enabled.
     * @return a copy of the provided rootEntity that can engage in transactions
     */
    public RootEntity clone(RootEntity rootEntity) {
        if (!repositories.containsKey(rootEntity))
            throw new NoTransactionsEnabledException();
        Commit initializationCommit = new Commit(null);  //since this is only a temporary commit the commitId doesn't really matter!
        //this is necessary to get a DataModel SPECIFIC class!
        RootEntity newRootEntity = DataModelInfo.constructRootEntity(rootEntity.getClass());
        Remote LOT = repositories.get(rootEntity).remote;
        Remote newLOT = new Remote();
        buildInitializationCommit(LOT, initializationCommit, rootEntity);
        //copy content of root entity
        for (Field field : DataModelInfo.getContentFields(newRootEntity)) {
            if (field.getAnnotation(CrossReference.class) != null)
                throw new RuntimeException("Cross-references not allowed in Root Entity!");
            field.setAccessible(true);
            try {
                field.set(newRootEntity, field.get(rootEntity));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        newLOT.put(LOT.getKey(rootEntity), newRootEntity);
        Repository repository = new Repository(newRootEntity, newLOT, new CommitId(0));
        new Pull(repository, initializationCommit);
        repositories.put(newRootEntity, repository);
        return newRootEntity;
    }

    /**
     * disable transactions and clean up
     */
    public void shutdown() {
        repositories.clear(); //effectively disabling transactions
        commits.clear();
    }


    /**
     * build {@link Remote} of a given {@link MutableObject} recursively
     */
    private void buildLogicalObjectTree(Remote LOT, MutableObject dme, CommitId commitId) {
        LOT.createObjectState(dme, commitId);
        ArrayList<ChildEntity<?>> children = DataModelInfo.getChildren(dme);
        for (ChildEntity<?> child : children) {
            buildLogicalObjectTree(LOT, child, commitId);
        }
    }

    /**
     * build a commit used for initialization by parsing the content of a given {@link MutableObject} recursively
     * into a {@link Remote} and adding the object to the commits' {@link Commit#creationRecords}
     */
    private void buildInitializationCommit(Remote LOT, Commit commit, MutableObject dme) {
        if (!(dme instanceof RootEntity))
            commit.creationRecords.put(LOT.getKey(dme), dme.constructorParameterStates(LOT));
        ArrayList<ChildEntity<?>> children = DataModelInfo.getChildren(dme);
        for (ChildEntity<?> child : children) {
            buildInitializationCommit(LOT, commit, child);
        }
    }

    //only used by Pull  //TODO same as JSON-parser?
    private static class CrossReferenceToDo {
        MutableObject dme;
        ObjectState objectKey, crossReferenceKey;
        Field ObjectField;
        CrossReferenceToDo(MutableObject dme, ObjectState objectKey, ObjectState crossReferenceKey, Field ObjectField) {
            this.dme = dme;
            this.objectKey = objectKey;
            this.crossReferenceKey = crossReferenceKey;
            this.ObjectField = ObjectField;
        }
    }


    //=============these methods are called synchronized per RootEntity and therefore package-private=================//

    Commit commit(RootEntity rootEntity) {
        Repository repository = repositories.get(rootEntity);
        //ensure transactions are enabled for rootEntity
        if (repository == null)
            throw new NoTransactionsEnabledException();
        //skip "empty" commits
        if (repository.hasNoLocalChanges())
            return null;
        Commit commit = new Commit(currentCommitId);
        currentCommitId = CommitId.increment(currentCommitId);
        Remote remote = repository.remote;

        //create ModificationRecords for DELETED objects
        //this becomes tricky because commits can be reverted in which case deletionRecords become creationRecords.
        //therefore, a deletionRecords objectState must contain the state BEFORE this commit. In order to achieve this,
        //deletions are handled first (to not include changes in owner/key)
        List<ChildEntity<?>> removeFromLOT = new ArrayList<>();
        ChildEntity<?> te = repository.getOneDeletion();
        while (te != null) {
            ObjectState state = remote.getKey(te);
            /*//cross-reference-states point at states in the remote that were up-to-date when this state was constructed.
            //underlying objects and their states may have changed by now, so we need to trace their changes through
            //the commits
            for (Map.Entry<Field, ObjectState> entry : state.crossReferences.entrySet()) {
                ObjectState crossReferenceState = entry.getValue();
                if (crossReferenceState == null)
                    continue;
                Field field = entry.getKey();
                field.setAccessible(true);
                //go through all commits that happened after states' construction
                for (Commit c : commits.tailMap(crossReferenceState.creationId, false).values()) {
                    *//*if (c.deletionRecords.containsKey(crossReferenceState)) {
                        //throw;
                    }*//*
                    if (c.changeRecords.containsKey(crossReferenceState)) {
                        crossReferenceState = c.changeRecords.get(crossReferenceState);
                    }
                }
                state.crossReferences.put(field, crossReferenceState);
            }*/


            commit.deletionRecords.put(state, te.constructorParameterStates(remote));
            //don't remove from remote yet, because this destroys owner information for possible deletion of children
            removeFromLOT.add(te);
            repository.removeDeletion(te);
            te = repository.getOneDeletion();
        }

        //create ModificationRecords for CREATED objects. This may cause other creation or changes to be handled first
        te = repository.getOneCreation();
        while (te != null) {
            commitCreation(repository, commit, te);
            te = repository.getOneCreation();
        }

        //create ModificationRecords for remaining CHANGED objects
        MutableObject dme = repository.getOneChange();
        while (dme != null) {
            commitChange(repository, commit, dme);
            dme = repository.getOneChange();
        }

        //now it is safe to remove all entries from remote
        for (ChildEntity<?> t : removeFromLOT) {
            remote.removeValue(t);
        }
        //clear deltas of the repository
        repository.clearUncommittedChanges();
        repository.currentCommitId = commit.commitId;
        synchronized (commits) {
            commits.put(commit.commitId, commit);
            if (history != null)
                history.addToOngoingCommit(commit);
        }
        if (verbose) System.out.println("\n========== COMMITTED "+ commit);
        return commit;
    }


    /**
     * process a local creation into a creationRecord. Makes sure that all {@link ObjectState}s used either
     * for construction parameters or cross-references, are up-to-date
     */
    private ObjectState commitCreation(Repository repository, Commit commit, ChildEntity<?> te) {
        //creationRecord contains states needed to construct this object. These states have to be present in the
        //remote. The method makes sure this is the case by recursively processing these creation or changes first

        //loop over all objects needed for construction. Non MutableObjects are not included
        for (MutableObject dme : te.constructorParameterMutables()) {
            if (dme instanceof ChildEntity<?> && repository.locallyCreatedContains((ChildEntity<?>) dme))
                commitCreation(repository, commit, (ChildEntity<?>) dme);
            else if (repository.locallyChangedContains(dme))
                commitChange(repository, commit, dme);
        }
        //te is not currently present in remote, so generates a NEW state and put it in remote
        ObjectState newKey = repository.remote.createObjectState(te, commit.commitId);
        //now its save to get the states of owner/keys from the remote and create the creation record with them
        commit.creationRecords.put(newKey, te.constructorParameterStates(repository.remote));
        //log of from creation tasks
        repository.removeCreation(te);

        //the newly created state may reference other states in cross-references, that may become outdated with this commit
        //avoid this by deploying the same strategy as above
        makeSureCrossReferencedStatesAreInRemote(repository, commit, newKey.crossReferences);
        return newKey;
    }


    /**
     * process a local change into a changeRecord. Makes sure that all {@link ObjectState}s used in
     * cross-references, are up-to-date
     */
    private ObjectState commitChange(Repository repository, Commit commit, MutableObject dme) {
        ObjectState before = repository.remote.getKey(dme);
        //remove old state from remote first to enable the creation of a new state for this object
        repository.remote.removeValue(dme);
        ObjectState after = repository.remote.createObjectState(dme, commit.commitId);
        commit.changeRecords.put(before, after);
        //log of from change tasks
        repository.removeChange(dme);

        //the newly created state may reference other states in cross-references, that may become outdated with this commit
        makeSureCrossReferencedStatesAreInRemote(repository, commit, after.crossReferences);
        return after;
    }

    private void makeSureCrossReferencedStatesAreInRemote(Repository repository, Commit commit, Map<Field, ObjectState> crossReferences) {
        for (Map.Entry<Field, ObjectState> crossReference : crossReferences.entrySet()) {
            //get the object the cross-reference is pointing at
            MutableObject dme = repository.remote.get(crossReference.getValue());
            if (dme != null) {
                //update or create object first. If the object points back at this due to the cross-reference being
                //circular, this is already logged of from local creations
                if (dme instanceof ChildEntity<?> && repository.locallyCreatedContains((ChildEntity<?>) dme))
                    crossReference.setValue(commitCreation(repository, commit, (ChildEntity<?>) dme));
                else if (repository.locallyChangedContains(dme))
                    crossReference.setValue(commitChange(repository, commit, dme));
            }
        }
    }


    //---------------PULL---------------//

    boolean pull(RootEntity rootEntity) {
        return new Pull(rootEntity).pulledSomething;
    }

    /**
     * Utility class to hold fields needed during a pull
     */
    private class Pull {
        boolean pulledSomething;
        Repository repository;
        Remote remote;
        Map<ObjectState, Object[]> creationChores, deletionChores;
        Map<ObjectState, ObjectState> changeChores;
        List<CrossReferenceToDo> crossReferences = new ArrayList<>();

        //this constructor is used to do an initializationPull
        private Pull(Repository repository, Commit initializationCommit) {
            this.repository = repository;
            repository.ongoingPull = true;
            pullOneCommit(initializationCommit);
            repository.ongoingPull = false;
            repository.currentCommitId = new CommitId(0);
        }

        //used for normal pulls
        private Pull(RootEntity rootEntity) {
            repository = repositories.get(rootEntity);
            if (repository == null)
                throw new NoTransactionsEnabledException();
            List<Commit> commitsToPull;
            //make sure no commits are added to the commit-list while pull copies the list
            synchronized (commits) {
                if (commits.isEmpty())
                    return;
                if (commits.lastKey() == repository.currentCommitId)
                    return;
                repository.ongoingPull = true;
                commitsToPull = new ArrayList<>(commits.tailMap(repository.currentCommitId, false).values());
            }
            for (Commit commit : commitsToPull) {
                if (verbose) System.out.println("\n========== PULLING "+ commit);
                pullOneCommit(commit);
            }
            if (!commitsToPull.isEmpty())
                pulledSomething = true;
            repository.ongoingPull = false;
            cleanUpUnnecessaryCommits();
        }

        //used for redos/undos
        private Pull(RootEntity rootEntity, Commit commit) {
            repository = repositories.get(rootEntity);
            if (repository == null)
                throw new NoTransactionsEnabledException();
            repository.ongoingPull = true;
            pullOneCommit(commit);
            repository.ongoingPull = false;
            repository.currentCommitId = commit.commitId;
            cleanUpUnnecessaryCommits();
        }


        /**
         * pull one commit from the remote and apply its changes to the data model
         * @param commit the commit to be pulled
         */
        private void pullOneCommit(Commit commit) {
            //copy modificationRecords to safely cross things off without changing commit itself!
            creationChores = new HashMap<>(commit.creationRecords);
            deletionChores = commit.deletionRecords;  //no removing
            changeChores = new HashMap<>();           //map changeChores with AFTER as key!
            for (Map.Entry<ObjectState, ObjectState> entry : commit.changeRecords.entrySet())
                changeChores.put(entry.getValue(), entry.getKey());
            remote = repository.remote;

            //DELETION - Assumes Deletion Records are created for all subsequent children!!!
            for (Map.Entry<ObjectState, Object[]> entry : deletionChores.entrySet()) {
                if (verbose) System.out.println(">deleting "+entry.getKey().clazz.getSimpleName()+"["+entry.getKey().hashCode()+"]");
                ChildEntity<?> objectToDelete = (ChildEntity<?>) remote.get(entry.getKey());
                objectToDelete.onCleared();
                objectToDelete.getOwner().onChanged();
                objectToDelete.destruct();
                remote.removeValue(objectToDelete);
            }

            //CREATION - Recursion possible because of dependency on owner and cross-references
            Map.Entry<ObjectState, Object[]> creationRecord;
            while (!creationChores.isEmpty()) {
                creationRecord = creationChores.entrySet().iterator().next();
                try {
                    pullCreationRecord(creationRecord.getKey(), creationRecord.getValue());
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

            //CHANGE - Recursion possible because of dependency on cross-references
            Map.Entry<ObjectState, ObjectState> changeRecord;
            while (!changeChores.isEmpty()) {
                changeRecord = changeChores.entrySet().iterator().next();
                try {
                    pullChangeRecord(changeRecord.getValue(), changeRecord.getKey());
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

            //at last link the open cross-reference dependencies
            for (CrossReferenceToDo cr : crossReferences) {
                cr.ObjectField.setAccessible(true);
                try {
                    if (remote.get(cr.crossReferenceKey) == null)
                        throw new TransactionException("can't find "+cr.crossReferenceKey.clazz.getSimpleName()+"["+cr.crossReferenceKey.hashCode()
                                +"] in remote, cross referenced by "+remote.getKey(cr.dme).clazz.getSimpleName(), remote.getKey(cr.dme).hashCode());
                    cr.ObjectField.set(cr.dme, remote.get(cr.crossReferenceKey));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            crossReferences.clear();
            repository.currentCommitId = commit.commitId;
        }

        /**
         * removes obsolete commits that are no longer used by any {@link Repository}
         */
        private void cleanUpUnnecessaryCommits() {
            synchronized(commits) {
                CommitId earliestCommitInUse = repository.currentCommitId;
                for (Repository repository : repositories.values()) {
                    if (repository.currentCommitId.compareTo(earliestCommitInUse) < 0)
                        earliestCommitInUse = repository.currentCommitId;
                }
                commits.headMap(earliestCommitInUse, true).clear();
            }
        }


        /**
         * creates an object from a creation record and put its key into the {@link Remote}
         * @param objKey key of the object to be created
         * @param constructionParams list of objects needed for construction. These are {@link ObjectState}s if
         *                           the param is another {@link MutableObject} or an immutable object itself
         */
        private void pullCreationRecord(ObjectState objKey, Object[] constructionParams) throws IllegalAccessException {
            if (verbose) System.out.println(">creating "+objKey.clazz.getSimpleName()+"["+objKey.hashCode()+"]");
            //parse construction params into array
            Object[] params = new Object[constructionParams.length];
            for (int i=0; i<constructionParams.length; i++) {
                Object key = constructionParams[i];
                //DMEs need to be resolved to their objects which may need to be created/changed themselves
                if (key instanceof ObjectState) {
                    ObjectState state = (ObjectState) key;
                    if (creationChores.containsKey(state)) {
                        pullCreationRecord(state, creationChores.get(state));
                    }
                    //check if state exists in changeRecords (now mapped as <after, before>
                    else if (changeChores.containsKey(state)) {
                        pullChangeRecord(changeChores.get(state), state);
                    }
                    //now object can be safely accessed via LOT
                    params[i] = remote.get(key);
                    if (params[i] == null)
                        throw new TransactionException("remote didn't contain construction parameter "+i+" which is a "+state.clazz.getSimpleName(), key.hashCode());
                }
                //object is an immutable, no parsing needed
                else {
                    params[i] = key;
                }
            }
            //construct the object
            ChildEntity<?> objectToCreate = DataModelInfo.construct(objKey.clazz, params);
            imprintLogicalContentOntoObject(objKey, objectToCreate);
            //notify owner that a new child was created
            objectToCreate.getOwner().onChanged();
            //System.out.println("PULL: created key "+objKey.hashCode());
            remote.put(objKey, objectToCreate);
            creationChores.remove(objKey);
        }

        private void pullChangeRecord(ObjectState before, ObjectState after) throws IllegalAccessException {
            if (verbose) System.out.println(">changing "+before.clazz.getSimpleName()+"["+before.hashCode()+"] -> ["+after.hashCode()+"]");
            MutableObject objectToChange = remote.get(before);
            if (objectToChange == null)
                throw new TransactionException("remote didn't contain "+before.clazz.getSimpleName(), before.hashCode());

            imprintLogicalContentOntoObject(after, objectToChange);
            //notify potential wrappers about the change
            objectToChange.onChanged();
            //if (verbose) System.out.println("PULL: changed from "+before.hashCode()+" to "+after.hashCode());
            remote.put(after, objectToChange);
            changeChores.remove(after);
        }

        private void imprintLogicalContentOntoObject(ObjectState after, MutableObject dme) throws IllegalAccessException {
            for (Field field : DataModelInfo.getContentFields(dme)) {
                if (after.containsKey(field)) {
                    field.setAccessible(true);
                    field.set(dme, after.get(field));
                }
                //field is a cross-reference
                else if (after.crossReferences.containsKey(field)) {
                    if (after.crossReferences.get(field) != null) {
                        //save cross-references to do at the very end to avoid infinite recursion when cross-references point at each other!
                        crossReferences.add(new CrossReferenceToDo(dme, after, after.crossReferences.get(field), field));
                    }
                    else {
                        field.setAccessible(true);
                        field.set(dme, null);
                    }
                }
            }
        }
    }


    Commit undo(RootEntity rootEntity) {
        if (history == null)
            throw new RuntimeException("Undos/Redos were not enabled!");
        if (history.undosAvailable()) {
            Commit commit = history.head.self;
            commit.commitId = currentCommitId;
            history.head = history.head.previous;

            //create a reverted commit
            Commit revertedCommit = new Commit(commit.commitId);
            currentCommitId = CommitId.increment(currentCommitId);

            //deletions

            revertedCommit.creationRecords = commit.deletionRecords;
            revertedCommit.deletionRecords = commit.creationRecords;
            DualHashBidiMap<ObjectState, ObjectState> changes = new DualHashBidiMap<>();
            for (Map.Entry<ObjectState, ObjectState> entry : commit.changeRecords.entrySet()) {
                changes.put(entry.getValue(), entry.getKey());
            }
            revertedCommit.changeRecords = changes;

            if (verbose) System.out.println("\n========== UNDO "+ revertedCommit);
            new Pull(rootEntity, revertedCommit);

            synchronized (commits) {
                commits.put(revertedCommit.commitId, revertedCommit);
            }
            return revertedCommit;
        }
        return null;
    }

    Commit redo(RootEntity rootEntity) {
        if (history == null)
            throw new RuntimeException("Undos/Redos were not enabled!");
        if (history.redosAvailable()) {
            history.head = history.head.next;
            Commit commit = history.head.self;
            commit.commitId = currentCommitId;
            currentCommitId = CommitId.increment(currentCommitId);
            if (verbose) System.out.println("\n========== REDO "+ commit);
            new Pull(rootEntity, commit);
            synchronized (commits) {
                commits.put(commit.commitId, commit);
            }
            return commit;
        }
        return null;
    }

    public void createUndoState() {
        if (history == null)
            throw new RuntimeException("Undos/Redos are not enabled!");
        history.createUndoState();
    }
}