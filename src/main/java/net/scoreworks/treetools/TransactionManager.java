package net.scoreworks.treetools;

import net.scoreworks.treetools.commits.Commit;
import net.scoreworks.treetools.commits.InvertedCommit;
import net.scoreworks.treetools.exceptions.NoTransactionsEnabledException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


/**
 * Class that handles transactions of a data model. Transactions are realized by first
 * enabling transactions on a {@link RootEntity}. This creates an associated {@link Remote} that links each
 * {@link MutableObject} with an immutable {@link Remote.ObjectState}. This REMOTE state is therefore not affected by local changes, until it
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

    /** print messages for debug purposes */
    static boolean verbose;
    public void setVerbose(boolean verbose) {
        TransactionManager.verbose = verbose;
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
            Repository repository = new Repository(rootEntity, new CommitId());
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
        Remote remoteToClone = repositories.get(rootEntity).remote;
        //build an untracked initialization commit on the repository that is to be cloned
        Commit initializationCommit = Commit.buildInitializationCommit(remoteToClone, rootEntity);
        //create new repository
        Repository newRepository = new Repository(newRootEntity, repositories.get(rootEntity).currentCommitId);

        //copy content of root entity and put it in emerging remote as well
        for (Field field : DataModelInfo.getFields(newRootEntity)) {
            field.setAccessible(true);
            try {
                field.set(newRootEntity, field.get(rootEntity));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        newRepository.remote.put(remoteToClone.getKey(rootEntity), newRootEntity);

        //populate the data model from the initializationCommit
        new Pull(newRepository, initializationCommit);
        repositories.put(newRootEntity, newRepository);
        return newRootEntity;
    }

    /**
     * disable transactions and clean up
     */
    public void shutdown() {
        CommitId.reset();       //reset commit id counter
        ObjectId.reset();       //reset object id counter
        repositories.clear();   //effectively disabling transactions
        commits.clear();
        history = null;
    }

    public void createUndoState() {
        if (history == null)
            throw new RuntimeException("Undos/Redos are not enabled!");
        history.createUndoState();
    }

    /**
     * removes obsolete commits that are no longer used by any {@link Repository}
     */
    private void cleanUpUnnecessaryCommits() {
        synchronized(commits) {
            CommitId earliestCommitInUse = commits.lastKey();
            for (Repository repository : repositories.values()) {
                if (repository.currentCommitId.compareTo(earliestCommitInUse) < 0)
                    earliestCommitInUse = repository.currentCommitId;
            }
            commits.headMap(earliestCommitInUse, true).clear();
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
        Commit commit = new Commit(repository);
        //clear deltas of the repository
        repository.clearUncommittedChanges();
        repository.currentCommitId = commit.getCommitId();
        synchronized (commits) {
            commits.put(commit.getCommitId(), commit);
            if (history != null)
                history.ongoingCommit.add(commit);
        }
        repository.currentCommitId = commit.getCommitId();
        if (verbose) System.out.println("\n========== COMMITTED "+ commit);
        return commit;
    }

    boolean pull(RootEntity rootEntity) {
        Repository repository = repositories.get(rootEntity);
        if (repository == null)
            throw new NoTransactionsEnabledException();
        List<Commit> commitsToPull;
        //make sure no commits are added to the commit-list while pull copies the list
        synchronized (commits) {
            if (commits.isEmpty())
                return false;
            if (commits.lastKey() == repository.currentCommitId)
                return false;
            commitsToPull = new ArrayList<>(commits.tailMap(repository.currentCommitId, false).values());
        }
        if (commitsToPull.isEmpty())
            return false;
        for (Commit commit : commitsToPull) {
            if (verbose && commit.getCommitId() != null) System.out.println("\n========== PULLING "+ commit);
            new Pull(repository, commit);
        }
        cleanUpUnnecessaryCommits();
        return true;
    }

    /**
     * take the current commit at {@link History#head}, invert it and insert it with a proper {@link CommitId} in
     * {@link TransactionManager#commits}
     */
    Commit undo(RootEntity rootEntity) {
        Repository repository = repositories.get(rootEntity);
        if (repository == null)
            throw new NoTransactionsEnabledException();
        if (history == null)
            throw new RuntimeException("Undos/Redos were not enabled!");
        if (history.undosAvailable()) {
            Commit undoCommit = history.head.self;
            history.head = history.head.previous;
            //create a traced, inverted commit with
            Commit invertedCommit = new InvertedCommit(undoCommit);

            synchronized (commits) {
                commits.put(invertedCommit.getCommitId(), invertedCommit);
            }
            if (verbose) System.out.println("\n========== UNDO "+ invertedCommit);
            new Pull(repository, invertedCommit);
            cleanUpUnnecessaryCommits();
            return invertedCommit;
        }
        return null;
    }

    /**
     * take the current commit at {@link History#head}, and insert it with a proper {@link CommitId} in
     * {@link TransactionManager#commits}
     */
    Commit redo(RootEntity rootEntity) {
        Repository repository = repositories.get(rootEntity);
        if (repository == null)
            throw new NoTransactionsEnabledException();
        if (history == null)
            throw new RuntimeException("Undos/Redos were not enabled!");
        if (history.redosAvailable()) {
            history.head = history.head.next;
            //copy the commit and give it a proper id
            Commit commit = new Commit(history.head.self);
            synchronized (commits) {
                commits.put(commit.getCommitId(), commit);
            }
            if (verbose) System.out.println("\n========== REDO "+ commit);
            new Pull(repository, commit);
            cleanUpUnnecessaryCommits();
            return commit;
        }
        return null;
    }
}