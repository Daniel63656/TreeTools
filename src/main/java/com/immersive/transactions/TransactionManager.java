package com.immersive.transactions;

import com.immersive.transactions.annotations.CrossReference;
import com.immersive.transactions.exceptions.NoTransactionsEnabledException;
import com.immersive.transactions.exceptions.TransactionException;
import com.immersive.transactions.LogicalObjectTree.LogicalObjectKey;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


/**
 * Class following the singleton pattern that handles transactions of a data model.
 */
public class TransactionManager {
    private static final TransactionManager transactionManager = new TransactionManager();
    private TransactionManager() {}
    public static TransactionManager getInstance() {
        return transactionManager;
    }

    /** keep track of all existing workcopies */
    Map<RootEntity, Workcopy> workcopies = new HashMap<>();

    /** save all commits in a time-ordered manner*/
    final TreeMap<CommitId, Commit> commits = new TreeMap<>();  //TODO shouldn't this be workcopy specific?

    /** object responsible for grouping commits and providing a history for {@link TransactionManager#undo(RootEntity)}
     * and {@link TransactionManager#redo(RootEntity)} */
    History history;

    /**
     * {@link CommitId} for the next commit to come. Each {@link TransactionManager#commit(RootEntity)} will increment this value
     */
    private CommitId currentCommitId = new CommitId(1);

    /** print messages for debug purposes */
    private boolean verbose;
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean transactionsEnabled(RootEntity rootEntity) {
        return workcopies.containsKey(rootEntity);
    }

    /**
     * enables transactions for a data model. After this method is called once on the data model,
     * new workcopies can be retrieved with {@link TransactionManager#getWorkcopyOf(RootEntity)}
     */
    public void enableTransactionsForRootEntity(RootEntity rootEntity) {
        //transactions are enabled, if there exists at least one workcopy. If workcopy is empty, create the
        //first workcopy for the given rootEntity
        if (workcopies.isEmpty()) {
            LogicalObjectTree LOT = new LogicalObjectTree();
            Workcopy workcopy = new Workcopy(rootEntity, LOT, new CommitId(0));
            buildLogicalObjectTree(LOT, rootEntity);
            workcopies.put(rootEntity, workcopy);
        }
    }

    public void enableUndoRedos(int capactity) {
        history = new History(capactity);
    }

    /**
     * @param rootEntity root of the data model that is to be copied. Needs to have transactions enabled.
     * @return a copy of the provided rootEntity that can engage in transactions
     */
    public RootEntity getWorkcopyOf(RootEntity rootEntity) {
        if (!workcopies.containsKey(rootEntity))
            throw new NoTransactionsEnabledException();
        Commit initializationCommit = new Commit(null);  //since this is only a temporary commit the commitId doesn't really matter!
        //this is necessary to get a DataModel SPECIFIC class!
        RootEntity newRootEntity = DataModelInfo.constructRootEntity(rootEntity.getClass());
        LogicalObjectTree LOT = workcopies.get(rootEntity).LOT;
        LogicalObjectTree newLOT = new LogicalObjectTree();
        buildInitializationCommit(LOT, initializationCommit, rootEntity);
        //copy content of root entity
        for (Field field : DataModelInfo.getContentFields(newRootEntity)) {
            if (field.getAnnotation(CrossReference.class) != null)
                throw new RuntimeException("Cross references not allowed in Root Entity!");
            field.setAccessible(true);
            try {
                field.set(newRootEntity, field.get(rootEntity));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        newLOT.put(LOT.getKey(rootEntity), newRootEntity);
        Workcopy workcopy = new Workcopy(newRootEntity, newLOT, new CommitId(0));
        new Pull(workcopy, initializationCommit);
        workcopies.put(newRootEntity, workcopy);
        return newRootEntity;
    }

    public CommitId getCurrentCommitId(RootEntity rootEntity) {
        if (!workcopies.containsKey(rootEntity))
            throw new NoTransactionsEnabledException();
        return workcopies.get(rootEntity).currentCommitId;
    }

    /**
     * disable transactions and clean up
     */
    public void shutdown() {
        workcopies.clear(); //effectively disabling transactions
        commits.clear();
    }


    /**
     * build {@link LogicalObjectTree} of a given {@link DataModelEntity} recursively
     */
    private void buildLogicalObjectTree(LogicalObjectTree LOT, DataModelEntity dme) {
        LOT.createLogicalObjectKey(dme);
        ArrayList<ChildEntity<?>> children = DataModelInfo.getChildren(dme);
        for (ChildEntity<?> child : children) {
            buildLogicalObjectTree(LOT, child);
        }
    }

    /**
     * build a commit used for initialization by parsing the content of a given {@link DataModelEntity} recursively
     * into a {@link LogicalObjectTree} and adding the object to the commits' {@link Commit#creationRecords}
     */
    private void buildInitializationCommit(LogicalObjectTree LOT, Commit commit, DataModelEntity dme) {
        if (!(dme instanceof RootEntity))
            commit.creationRecords.put(LOT.getKey(dme), dme.constructorParameterLOKs(LOT));
        ArrayList<ChildEntity<?>> children = DataModelInfo.getChildren(dme);
        for (ChildEntity<?> child : children) {
            buildInitializationCommit(LOT, commit, child);
        }
    }

    //only used by Pull
    private static class CrossReferenceToDo {
        DataModelEntity dme;
        LogicalObjectKey objectKey, crossReferenceKey;
        Field ObjectField;
        CrossReferenceToDo(DataModelEntity dme, LogicalObjectKey objectKey, LogicalObjectKey crossReferenceKey, Field ObjectField) {
            this.dme = dme;
            this.objectKey = objectKey;
            this.crossReferenceKey = crossReferenceKey;
            this.ObjectField = ObjectField;
        }
    }

    //=========these methods are synchronized per RootEntity and therefore package-private============

    //TODO make class to store local context
    Commit commit(RootEntity rootEntity) {
        Workcopy workcopy = workcopies.get(rootEntity);
        //ensure transactions are enabled for rootEntity
        if (workcopy == null)
            throw new NoTransactionsEnabledException();
        //skip "empty" commits
        if (workcopy.hasNoLocalChanges())
            return null;

        Commit commit = new Commit(currentCommitId);
        currentCommitId = CommitId.increment(currentCommitId);
        //get a reference to the LogicalObjectTree of the workcopy which is a representation of the "remote" state
        LogicalObjectTree remote = workcopy.LOT;
        //initialize collections that help execute the commit
        List<ChildEntity<?>> removeFromLOT = new ArrayList<>();

        //create ModificationRecords for CREATED objects. This may cause other creation or changes to be handled first
        ChildEntity<?> te = workcopy.getOneCreation();
        while (te != null) {
            commitCreation(workcopy, commit, remote, te);
            te = workcopy.getOneCreation();
        }

        //create ModificationRecords for remaining CHANGED objects
        DataModelEntity dme = workcopy.getOneChange();
        while (dme != null) {
            commitChange(workcopy, commit, remote, dme);
            dme = workcopy.getOneChange();
        }

        //create ModificationRecords for DELETED objects
        te = workcopy.getOneDeletion();
        while (te != null) {
            commit.deletionRecords.put(remote.getKey(te), te.constructorParameterLOKs(remote));
            //don't remove from remote yet, because this destroys owner information for possible deletion of children
            removeFromLOT.add(te);
            workcopy.removeDeletion(te);
            te = workcopy.getOneDeletion();
        }
        //now it is safe to remove all entries from remote
        for (ChildEntity<?> t : removeFromLOT) {
            remote.removeValue(t);
        }
        //clear workcopy deltas
        workcopy.clearUncommittedChanges();
        workcopy.currentCommitId = commit.commitId;
        synchronized (commits) {
            commits.put(commit.commitId, commit);
            if (history != null)
                history.addToOngoingCommit(commit);
        }
        return commit;
    }


    /**
     * process a local creation into a creationRecord. Makes sure that all {@link LogicalObjectKey}s used either
     * for construction parameters or cross-references, are up-to-date
     */
    private LogicalObjectKey commitCreation(Workcopy workcopy, Commit commit, LogicalObjectTree remote, ChildEntity<?> te) {
        //creationRecord contains LOKs needed to construct this object. These LOKs have to be present and the current
        //version. The method makes sure this is the case by recursively processing these creation or changes first

        //loop over all objects needed for construction. Non DataModelEntities are not included
        for (DataModelEntity dme : te.constructorParameterDMEs()) {
            if (dme instanceof ChildEntity<?> && workcopy.locallyCreatedContains((ChildEntity<?>) dme))
                commitCreation(workcopy, commit, remote, (ChildEntity<?>) dme);
            else if (workcopy.locallyChangedContains(dme))
                commitChange(workcopy, commit, remote, dme);
        }
        //te is not currently present in remote, so generates a NEW key and put it in remote
        LogicalObjectKey newKey = remote.createLogicalObjectKey(te);
        //now its save to get the LOKs of owner/keys from the remote and create the creation record with them
        commit.creationRecords.put(newKey, te.constructorParameterLOKs(remote));
        //log of from creation tasks
        workcopy.removeCreation(te);

        //the newly created key may reference LOKs in cross-references, that may become outdated with this commit
        //avoid this by deploying the same strategy as above
        for (Map.Entry<Field, LogicalObjectKey> crossReference : newKey.crossReferences.entrySet()) {
            //get the object the cross-reference is pointing at
            DataModelEntity dme = remote.get(crossReference.getValue());
            if (dme != null) {
                //update or create object first. If the object points back at this due to the cross-reference being
                //circular, this is already logged of from local creations
                if (dme instanceof ChildEntity<?> && workcopy.locallyCreatedContains((ChildEntity<?>) dme))
                    crossReference.setValue(commitCreation(workcopy, commit, remote, (ChildEntity<?>) dme));
                else if (workcopy.locallyChangedContains(dme))
                    crossReference.setValue(commitChange(workcopy, commit, remote, dme));
            }
        }
        return newKey;
    }

    private LogicalObjectKey commitChange(Workcopy workcopy, Commit commit, LogicalObjectTree remote, DataModelEntity dme) {
        LogicalObjectKey before = remote.getKey(dme);
        //remove old key from remote first to enable the change of a new key for this particular dme
        remote.removeValue(dme);
        LogicalObjectKey after = remote.createLogicalObjectKey(dme);
        commit.changeRecords.put(before, after);
        //log of from change tasks
        workcopy.removeChange(dme);

        //the newly created key may reference LOKs in cross-references, that may become outdated with this commit
        //avoid this by deploying the same strategy as above
        for (Map.Entry<Field, LogicalObjectKey> crossReference : after.crossReferences.entrySet()) {
            //get the object the cross-reference is pointing at
            DataModelEntity d = remote.get(crossReference.getValue());
            if (d != null) {
                //update or create object first. If the object points back at this due to the cross-reference being
                //circular, this is already logged of from local creations
                if (d instanceof ChildEntity<?> && workcopy.locallyCreatedContains((ChildEntity<?>) d))
                    crossReference.setValue(commitCreation(workcopy, commit, remote, (ChildEntity<?>) d));
                else if (workcopy.locallyChangedContains(d))
                    crossReference.setValue(commitChange(workcopy, commit, remote, d));
            }
        }
        return after;
    }


    Commit undo(RootEntity rootEntity) {
        if (history == null)
            throw new RuntimeException("Undos/Redos were not enabled!");
        if (history.undosAvailable()) {
            Commit commit = history.head.self;
            commit.commitId = currentCommitId;
            history.head = history.head.previous;
            new Pull(rootEntity, commit, true);
            //revert the commit for commit-list!
            Commit revertedCommit = new Commit(commit.commitId);
            currentCommitId = CommitId.increment(currentCommitId);
            revertedCommit.creationRecords = commit.deletionRecords;
            revertedCommit.deletionRecords = commit.creationRecords;
            DualHashBidiMap<LogicalObjectKey, LogicalObjectKey> changes = new DualHashBidiMap<>();
            for (Map.Entry<LogicalObjectKey, LogicalObjectKey> entry : commit.changeRecords.entrySet())
                changes.put(entry.getValue(), entry.getKey());
            revertedCommit.changeRecords = changes;
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
            new Pull(rootEntity, commit, false);
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

    //---------------PULL---------------//

    boolean pull(RootEntity rootEntity) {
        return new Pull(rootEntity).pulledSomething;
    }

    /**
     * Utility class to hold fields needed during a pull
     */
    private class Pull {
        boolean pulledSomething;
        Workcopy workcopy;
        LogicalObjectTree LOT;
        Map<LogicalObjectKey, Object[]> creationChores, deletionChores;
        Map<LogicalObjectKey, LogicalObjectKey> changeChores;
        List<CrossReferenceToDo> crossReferences = new ArrayList<>();

        //this constructor is used to do an initializationPull
        private Pull(Workcopy workcopy, Commit initializationCommit) {
            this.workcopy = workcopy;
            workcopy.ongoingPull = true;
            pullOneCommit(initializationCommit, false, false);
            workcopy.ongoingPull = false;
            workcopy.currentCommitId = new CommitId(0);
        }

        //used for normal pulls
        private Pull(RootEntity rootEntity) {
            workcopy = workcopies.get(rootEntity);
            if (workcopy == null)
                throw new NoTransactionsEnabledException();
            List<Commit> commitsToPull;
            //make sure no commits are added to the commit-list while pull copies the list
            synchronized (commits) {
                if (commits.isEmpty())
                    return;
                if (commits.lastKey() == workcopy.currentCommitId)
                    return;
                workcopy.ongoingPull = true;
                commitsToPull = new ArrayList<>(commits.tailMap(workcopy.currentCommitId, false).values());
            }
            for (Commit commit : commitsToPull) {
                pullOneCommit(commit, false, false);
            }
            if (!commitsToPull.isEmpty())
                pulledSomething = true;
            workcopy.ongoingPull = false;
            cleanUpUnnecessaryCommits();
        }

        //used for redos/undos
        private Pull(RootEntity rootEntity, Commit commit, boolean revert) {
            workcopy = workcopies.get(rootEntity);
            if (workcopy == null)
                throw new NoTransactionsEnabledException();
            workcopy.ongoingPull = true;
            pullOneCommit(commit, revert, true);
            workcopy.ongoingPull = false;
            workcopy.currentCommitId = commit.commitId;
            cleanUpUnnecessaryCommits();
        }


        private void pullOneCommit(Commit commit, boolean revert, boolean redoUndo) {
            if (!revert) {
                //copy modificationRecords to safely cross things off without changing commit itself!
                creationChores = new HashMap<>(commit.creationRecords);
                deletionChores = commit.deletionRecords;  //no removing
                changeChores = new HashMap<>();           //map changeChores with AFTER as key!
                for (Map.Entry<LogicalObjectKey, LogicalObjectKey> entry : commit.changeRecords.entrySet())
                    changeChores.put(entry.getValue(), entry.getKey());
            }
            else {
                creationChores = new HashMap<>(commit.deletionRecords);
                deletionChores = commit.creationRecords;            //no removing
                changeChores = new HashMap<>(commit.changeRecords); //map changeChores with BEFORE as key!
            }
            LOT = workcopy.LOT;

            //DELETION - Assumes Deletion Records are created for all subsequent children!!!
            for (Map.Entry<LogicalObjectKey, Object[]> entry : deletionChores.entrySet()) {
                ChildEntity<?> objectToDelete = (ChildEntity<?>) LOT.get(entry.getKey());
                objectToDelete.onCleared();
                objectToDelete.getOwner().onChanged();
                objectToDelete.destruct();
                LOT.removeValue(objectToDelete);
            }

            //CREATION - Recursion possible because of dependency on owner and cross-references
            Map.Entry<LogicalObjectKey, Object[]> creationRecord;
            while (!creationChores.isEmpty()) {
                creationRecord = creationChores.entrySet().iterator().next();
                try {
                    pullCreationRecord(creationRecord.getKey(), creationRecord.getValue());
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

            //CHANGE - Recursion possible because of dependency on cross-references
            Map.Entry<LogicalObjectKey, LogicalObjectKey> changeRecord;
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
                    if (LOT.get(cr.crossReferenceKey) == null)
                        throw new TransactionException("error linking cross references for "+LOT.getKey(cr.dme).hashCode(), cr.crossReferenceKey.hashCode());
                    cr.ObjectField.set(cr.dme, LOT.get(cr.crossReferenceKey));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            crossReferences.clear();
            workcopy.currentCommitId = commit.commitId;
        }

        /**
         * removes obsolete commits that are no longer used by any workcopy
         */
        private void cleanUpUnnecessaryCommits() {
            synchronized(commits) {
                CommitId earliestCommitInUse = workcopy.currentCommitId;
                for (Workcopy workcopy : workcopies.values()) {
                    if (workcopy.currentCommitId.compareTo(earliestCommitInUse) < 0)
                        earliestCommitInUse = workcopy.currentCommitId;
                }
                commits.headMap(earliestCommitInUse, true).clear();
            }
        }


        /**
         * creates an object from a creation record and put its key into the {@link LogicalObjectTree}
         * @param objKey key of the object to be created
         * @param constructionParams list of objects needed for construction. These are {@link LogicalObjectKey}s if
         *                           the param is another {@link DataModelEntity} or an immutable object itself
         * @throws IllegalAccessException
         */
        private void pullCreationRecord(LogicalObjectKey objKey, Object[] constructionParams) throws IllegalAccessException {
            //parse construction params into array
            Object[] params = new Object[constructionParams.length];
            for (int i=0; i<constructionParams.length; i++) {
                Object key = constructionParams[i];
                //DMEs need to be resolved to their objects which may need to be created/changed themselves
                if (key instanceof LogicalObjectKey) {
                    LogicalObjectKey LOK = (LogicalObjectKey) key;
                    if (creationChores.containsKey(LOK)) {
                        pullCreationRecord(LOK, creationChores.get(LOK));
                    }
                    //check if LOK exists in changeRecords (now mapped as <after, before>
                    else if (changeChores.containsKey(LOK)) {
                        pullChangeRecord(changeChores.get(LOK), LOK);
                    }
                    //now object can be safely accessed via LOT
                    params[i] = LOT.get(key);
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
            LOT.put(objKey, objectToCreate);
            creationChores.remove(objKey);
        }

        private void pullChangeRecord(LogicalObjectKey before, LogicalObjectKey after) throws IllegalAccessException {
            DataModelEntity objectToChange = LOT.get(before);
            if (objectToChange == null)
                throw new TransactionException("object to change is null!", before.hashCode());

            imprintLogicalContentOntoObject(after, objectToChange);
            //notify potential wrappers about the change
            objectToChange.onChanged();
            //if (verbose) System.out.println("PULL: changed from "+before.hashCode()+" to "+after.hashCode());
            LOT.put(after, objectToChange);
            changeChores.remove(after);
        }

        private void imprintLogicalContentOntoObject(LogicalObjectKey after, DataModelEntity dme) throws IllegalAccessException {
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
}