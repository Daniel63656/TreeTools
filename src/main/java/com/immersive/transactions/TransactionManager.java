package com.immersive.transactions;

import com.immersive.transactions.annotations.CrossReference;
import com.immersive.transactions.exceptions.NoTransactionsEnabledException;
import com.immersive.transactions.exceptions.TransactionException;
import com.immersive.transactions.LogicalObjectTree.LogicalObjectKey;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    final TreeMap<CommitId, Commit> commits = new TreeMap<>();  //TODO shuldn't this be workcopy specific?

    /** object responsible for grouping commits and providing a history for {@link TransactionManager#undo(RootEntity)}
     * and {@link TransactionManager#redo(RootEntity)} */
    History history;

    /**
     * {@link CommitId} for the next commit to come. Each {@link TransactionManager#commit(RootEntity)} will increment this value
     */
    private CommitId currentCommitId = new CommitId(1);

    /** print messages about object creation, deletion and changes for debug purposes */
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
        RootEntity newRootEntity = (RootEntity) DataModelInfo.construct(rootEntity.getClass());
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
            commit.creationRecords.put(LOT.getKey(dme), dme.getConstructorParamsAsKeys(LOT));
        ArrayList<ChildEntity<?>> children = DataModelInfo.getChildren(dme);
        for (ChildEntity<?> child : children) {
            buildInitializationCommit(LOT, commit, child);
        }
    }

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

    Commit commit(RootEntity rootEntity) {
        Workcopy workcopy = workcopies.get(rootEntity);
        //ensure transactions are enabled for rootEntity
        if (workcopy == null)
            throw new NoTransactionsEnabledException();
        //skip "empty" commits
        if (workcopy.locallyCreatedOrChanged.isEmpty() && workcopy.locallyDeleted.isEmpty())
            return null;

        Commit commit = new Commit(currentCommitId);
        currentCommitId = CommitId.increment(currentCommitId);
        //get a reference to the LogicalObjectTree of the workcopy which is a representation of the "remote" state
        LogicalObjectTree remote = workcopy.LOT;
        //initialize collections that help execute the commit
        List<ChildEntity<?>> removeFromLOT = new ArrayList<>();
        Set<LogicalObjectKey> keysCreatedSoFar = new HashSet<>();

        //create ModificationRecords in commit for CREATED or CHANGED objects
        while (!workcopy.locallyCreatedOrChanged.isEmpty()) {
            DataModelEntity dme = workcopy.locallyCreatedOrChanged.iterator().next();
            //ignore creation or change if object got deleted in the end
            if (dme instanceof ChildEntity<?>) {
                if (workcopy.locallyDeleted.contains(dme)) {
                    workcopy.locallyCreatedOrChanged.remove(dme);
                    //remove deletion instruction if chore was a creation that got removed right now
                    if (!remote.containsValue(dme) || (remote.containsValue(dme) && keysCreatedSoFar.contains(remote.getKey(dme))))
                        workcopy.locallyDeleted.remove(dme);
                    continue;
                }
            }
            commitCreationOrChange(workcopy.locallyCreatedOrChanged, commit, remote, dme, keysCreatedSoFar);
        }

        //create ModificationRecords for DELETED objects
        Iterator<ChildEntity<?>> iterator = workcopy.locallyDeleted.iterator();
        while (iterator.hasNext()) {
            ChildEntity<?> te = iterator.next();
            commit.deletionRecords.put(remote.getKey(te), te.getConstructorParamsAsKeys(remote));  //TODO notify wrappers?
            //don't remove from remote yet, because this destroys owner information for possible deletion entries of children!
            //save this action in a list to do it at the end of the deletion part of the commit instead
            removeFromLOT.add(te);
            iterator.remove();
        }
        //now it is safe to remove all entries from remote
        for (ChildEntity<?> te : removeFromLOT) {
            remote.removeValue(te);
        }
        //clear workcopies deltas
        workcopy.locallyDeleted.clear();
        workcopy.locallyCreatedOrChanged.clear();
        workcopy.currentCommitId = commit.commitId;
        synchronized (commits) {
            commits.put(commit.commitId, commit);
            if (history != null)
                history.addToOngoingCommit(commit);
        }
        return commit;
    }

    private void commitCreationOrChange(Set<DataModelEntity> createdOrChanged, Commit commit, LogicalObjectTree remote, DataModelEntity dme, Set<LogicalObjectKey> keysCreatedSoFar) {
        //CREATION - not in remote before or only because a cross-reference created it!
        if (!remote.containsValue(dme) || (remote.containsValue(dme) && keysCreatedSoFar.contains(remote.getKey(dme)))) {
            if (dme instanceof RootEntity)
                throw new RuntimeException("Root Entity can only be CHANGED by commits!");
            //object that are needed for current object construction are also subject to creation or change, so handle them first
            for (DataModelEntity obj : dme.getConstructorParamsAsObjects()) {
                if (createdOrChanged.contains(obj)) {
                    commitCreationOrChange(createdOrChanged, commit, remote, obj, keysCreatedSoFar);
                }
            }
            //dme is not currently present in remote, so generates a NEW key and put it in remote
            LogicalObjectKey newKey = remote.createLogicalObjectKey(dme);
            keysCreatedSoFar.add(newKey);
            //now its save to get the LOK from owner and keys from the remote
            commit.creationRecords.put(newKey, dme.getConstructorParamsAsKeys(remote));  //TODO notify wrappers?
        }

        //CHANGE
        else {
            LogicalObjectKey before = remote.getKey(dme);
            //remove old key from remote first to enable the creation of a new key for this particular dme
            remote.removeValue(dme);
            LogicalObjectKey after = remote.createLogicalObjectKey(dme);
            keysCreatedSoFar.add(after);

            //migrate any dependency on old key to new one before old key is lost
            //if two objects cross-reference each other, one of them is handled first and therefore receives the new LOK in its subscribedLOKs!
            for (Map.Entry<LogicalObjectKey, Field> subscribed : before.subscribedLOKs.entrySet()) {
                if (!keysCreatedSoFar.contains(subscribed.getKey())) {
                    //object subscribed is not itself part of a change or not done yet so add a chore!
                    createdOrChanged.add(remote.get(subscribed.getKey()));
                }
                else {
                    //make all LOKs previously subscribed to before subscribe to after and change their field!
                    //this is the only case an otherwise immutable LOK gets modified!
                    after.subscribedLOKs.put(subscribed.getKey(), subscribed.getValue());
                    subscribed.getKey().put(subscribed.getValue(), after);
                }
            }
            commit.changeRecords.put(before, after);  //TODO notify wrappers?
        }
        createdOrChanged.remove(dme);
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

        /**
         *
         * @param commit
         * @param revert if true, the changes of the specified commit are reverted instead of pulled
         * @param redoUndo important for linking cross-references
         */
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
                objectToDelete.onWrappedCleared();
                objectToDelete.getOwner().onWrappedChanged();
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
                    //since subscribedLOKs are MUTABLE over commits, they have to be RESTORED when doing undos/redos!
                    if (redoUndo)
                        cr.crossReferenceKey.subscribedLOKs.put(cr.objectKey, cr.ObjectField);  //put new value in
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            //remove invalid subscribers by checking if they are present in current LOT for each changed object
            if (redoUndo) {
                if (!revert) {
                    for (LogicalObjectKey after : commit.changeRecords.values())
                        after.subscribedLOKs.keySet().removeIf(LOK -> !LOT.containsKey(LOK));
                }
                else {
                    for (LogicalObjectKey after : commit.changeRecords.keySet())
                        after.subscribedLOKs.keySet().removeIf(LOK -> !LOT.containsKey(LOK));
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

        //-----recursive functions-----//

        private void pullCreationRecord(LogicalObjectKey objKey, Object[] constKeys) throws IllegalAccessException {
            Object[] params = new Object[constKeys.length];
            for (int i=0; i<constKeys.length; i++) {
                Object key = constKeys[i];
                if (key instanceof LogicalObjectKey) {
                    LogicalObjectKey LOK = (LogicalObjectKey) key;
                    if (creationChores.containsKey(LOK)) {
                        pullCreationRecord(LOK, creationChores.get(LOK));
                    }
                    //check if AFTER exists in changeRecords!
                    if (changeChores.containsKey(LOK)) {
                        pullChangeRecord(changeChores.get(LOK), LOK);
                    }
                    //now object can be safely assigned using LOT
                    params[i] = LOT.get(key);
                }
                else {
                    params[i] = key;
                }
            }
            DataModelEntity objectToCreate = DataModelInfo.construct(objKey.clazz, params);
            imprintLogicalContentOntoObject(objKey, objectToCreate);
            if (objectToCreate instanceof ChildEntity) {
                ((ChildEntity<?>) objectToCreate).getOwner().onWrappedChanged();
            }
            //System.out.println("PULL: created key "+objKey.hashCode());
            LOT.put(objKey, objectToCreate);
            creationChores.remove(objKey);
        }

        private void pullChangeRecord(LogicalObjectKey before, LogicalObjectKey after) throws IllegalAccessException {
            DataModelEntity objectToChange = LOT.get(before);
            if (objectToChange == null)
                throw new TransactionException("object to change is null!", before.hashCode());
            imprintLogicalContentOntoObject(after, objectToChange);
            objectToChange.onWrappedChanged();
            //System.out.println("PULL: changed from "+before.hashCode()+" to "+after.hashCode());
            LOT.put(after, objectToChange);
            changeChores.remove(after);
        }

        private void imprintLogicalContentOntoObject(LogicalObjectKey after, DataModelEntity dme) throws IllegalAccessException {
            for (Field field : DataModelInfo.getContentFields(dme)) {
                if (after.containsKey(field)) {
                    //save cross-references to do at the very end to avoid infinite recursion when cross-references point at each other!
                    if (field.getAnnotation(CrossReference.class) != null) {
                        if (after.get(field) != null)
                            crossReferences.add(new CrossReferenceToDo(dme, after, (LogicalObjectKey) after.get(field), field));
                        else {
                            field.setAccessible(true);
                            field.set(dme, null);
                        }
                        continue;
                    }
                    //set the field
                    field.setAccessible(true);
                    field.set(dme, after.get(field));
                }
            }
        }
    }
}