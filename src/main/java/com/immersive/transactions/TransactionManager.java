package com.immersive.transactions;

import com.immersive.transactions.annotations.CrossReference;
import com.immersive.transactions.commits.CollapsedCommit;
import com.immersive.transactions.commits.Commit;
import com.immersive.transactions.commits.CommitId;
import com.immersive.transactions.exceptions.NoTransactionsEnabledException;
import com.immersive.transactions.exceptions.TransactionException;
import com.immersive.transactions.Remote.ObjectState;

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
            Remote remote = new Remote();
            CommitId  initializationId = new CommitId(0);
            Repository repository = new Repository(rootEntity, remote, initializationId);
            buildRemote(remote, rootEntity, initializationId);
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

        //get a new data model-specific rootEntity
        RootEntity newRootEntity = DataModelInfo.constructRootEntity(rootEntity.getClass());
        Remote remote = repositories.get(rootEntity).remote;
        Remote newRemote = new Remote();
        Commit initializationCommit = Commit.buildInitializationCommit(remote, rootEntity);

        //copy content of root entity and put it in remote as well
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
        newRemote.put(remote.getKey(rootEntity), newRootEntity);

        //create a new repository and populate the data model from the initializationCommit
        Repository repository = new Repository(newRootEntity, newRemote, initializationCommit.getCommitId());
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
        history = null;
    }


    /**
     * build {@link Remote} of a given {@link MutableObject} recursively
     */
    //TODO put as remote constructor?
    private void buildRemote(Remote remote, MutableObject dme, CommitId commitId) {
        remote.createObjectState(dme, commitId);
        ArrayList<ChildEntity<?>> children = DataModelInfo.getChildren(dme);
        for (ChildEntity<?> child : children) {
            buildRemote(remote, child, commitId);
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
        //create the commit
        Commit commit = new Commit(currentCommitId, repository);
        currentCommitId = CommitId.increment(currentCommitId);
        //clear deltas of the repository
        repository.clearUncommittedChanges();
        repository.currentCommitId = commit.getCommitId();
        synchronized (commits) {
            commits.put(commit.getCommitId(), commit);
            if (history != null)
                history.ongoingCommit.add(commit);
        }
        if (verbose) System.out.println("\n========== COMMITTED "+ commit);
        return commit;
    }


    //---------------PULL---------------//

    private static class CrossReferenceToDo {
        MutableObject dme;
        ObjectState state, crossReferencedState;
        Field objectField;
        CrossReferenceToDo(MutableObject dme, ObjectState state, ObjectState crossReferencedState, Field objectField) {
            this.dme = dme;
            this.state = state;
            this.crossReferencedState = crossReferencedState;
            this.objectField = objectField;
        }
    }

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
            repository.currentCommitId = commit.getCommitId();
            cleanUpUnnecessaryCommits();
        }


        /**
         * pull one commit from the remote and apply its changes to the data model
         * @param commit the commit to be pulled
         */
        private void pullOneCommit(Commit commit) {
            //copy modificationRecords to safely cross things off without changing commit itself!
            creationChores = new HashMap<>(commit.getCreationRecords());
            deletionChores = commit.getDeletionRecords();  //no removing
            changeChores = new HashMap<>(commit.getInvertedChanges()); //map changeChores with AFTER as key!

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

                //cross-referenced-states only point at valid states in the remote when this state was constructed.
                //underlying objects and their states may have changed by now, so we need to trace their changes through
                //all commits that happened after states' construction up to this commit
                for (Commit c : commits.subMap(cr.state.creationId, false, commit.getCommitId(), true).values()) {
                    cr.crossReferencedState = c.traceForward(cr.crossReferencedState);
                }

                MutableObject referencedObject = remote.get(cr.crossReferencedState);
                if (referencedObject == null)
                    throw new TransactionException("can't find "+cr.crossReferencedState.clazz.getSimpleName()+"["+cr.crossReferencedState.hashCode()+"] in remote, cross referenced by "+remote.getKey(cr.dme).clazz.getSimpleName(), remote.getKey(cr.dme).hashCode());
                cr.objectField.setAccessible(true);
                try {
                    cr.objectField.set(cr.dme, referencedObject);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            crossReferences.clear();
            repository.currentCommitId = commit.getCommitId();
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
                        throw new TransactionException("remote didn't contain "+state.clazz.getSimpleName()+" with id["+key.hashCode()+"] needed during creation of "+objKey.clazz.getSimpleName(), objKey.hashCode());
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

        private void imprintLogicalContentOntoObject(ObjectState state, MutableObject dme) throws IllegalAccessException {
            for (Field field : DataModelInfo.getContentFields(dme)) {
                if (state.containsKey(field)) {
                    field.setAccessible(true);
                    field.set(dme, state.get(field));
                }
                //field is a cross-reference
                else if (state.crossReferences.containsKey(field)) {
                    if (state.crossReferences.get(field) != null) {
                        //save cross-references to do at the very end to avoid infinite recursion when cross-references point at each other!
                        crossReferences.add(new CrossReferenceToDo(dme, state, state.crossReferences.get(field), field));
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
            CollapsedCommit undoCommit = history.head.self;
            history.head = history.head.previous;
            //create an inverted commit
            CollapsedCommit invertedCommit = CollapsedCommit.buildInvertedCommit(currentCommitId, undoCommit);
            currentCommitId = CommitId.increment(currentCommitId);

            synchronized (commits) {
                commits.put(invertedCommit.getCommitId(), invertedCommit);
            }
            if (verbose) System.out.println("\n========== UNDO "+ invertedCommit);
            new Pull(rootEntity, invertedCommit);
            return invertedCommit;
        }
        return null;
    }

    Commit redo(RootEntity rootEntity) {
        if (history == null)
            throw new RuntimeException("Undos/Redos were not enabled!");
        if (history.redosAvailable()) {
            history.head = history.head.next;
            //copy the commit and give it a proper id
            CollapsedCommit commit = new CollapsedCommit(currentCommitId, history.head.self);
            currentCommitId = CommitId.increment(currentCommitId);
            synchronized (commits) {
                commits.put(commit.getCommitId(), commit);
            }
            if (verbose) System.out.println("\n========== REDO "+ commit);
            new Pull(rootEntity, commit);
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