package com.immersive.transactions;

import com.immersive.transactions.Remote.ObjectState;
import com.immersive.transactions.commits.Commit;
import com.immersive.transactions.commits.CommitId;
import com.immersive.transactions.exceptions.NoTransactionsEnabledException;
import com.immersive.transactions.exceptions.TransactionException;

import java.util.*;


/**
 * Transactional class for the root element of the data model. This class holds transactional
 * methods like push, pull, redo and undo.
 */
public abstract class RootEntity implements MutableObject {
    final TransactionManager tm = TransactionManager.getInstance();

    /**
     * Set that holds all {@link WrapperScope}s. As member of this class, this field
     * is not part of the data model itself and is ignored by the transactional system and {@link JsonParser}
     */
    final Set<WrapperScope> wrapperScopes = new HashSet<>();

    public void addWrapperScope(WrapperScope scope) {
        wrapperScopes.add(scope);
    }
    public void removeWrapperScope(WrapperScope scope) {
        wrapperScopes.remove(scope);
    }

    @Override
    public List<Wrapper<?>> getRegisteredWrappers() {
        List<Wrapper<?>> wrappers = new ArrayList<>();
        for (WrapperScope scope : wrapperScopes) {
            if (scope.registeredWrappers.containsKey(this))
                wrappers.add(scope.registeredWrappers.get(this));
        }
        return wrappers;
    }

    @Override
    public Wrapper<?> getRegisteredWrapper(WrapperScope scope) {
        return scope.registeredWrappers.get(this);
    }


    @Override
    public void onCleared() {
        //implement and catch this method here to make sure no specific RootEntity can be cleared
        throw new RuntimeException("Can't remove the root of a data model!");
    }

    @Override
    public void onChanged() {
        for (WrapperScope scope : wrapperScopes) {
            if (scope.registeredWrappers.containsKey(this))
                scope.registeredWrappers.get(this).onWrappedChanged();
        }
    }

    @Override
    public Class<?>[] constructorParameterTypes() {
        return new Class<?>[0];
    }
    @Override
    public ObjectState[] constructorParameterStates(Remote remote) {
        return new ObjectState[0];
    }

    @Override
    public MutableObject[] constructorParameterMutables() { return new MutableObject[0];}

    @Override
    public RootEntity getRootEntity() {
        return this;
    }

    @Override
    public MutableObject getCorrespondingObjectIn(RootEntity dstRootEntity) {
        return getObjectSynchronizedIn(this, dstRootEntity);
    }

    public synchronized Commit commit() {
        return tm.commit(this);
    }

    public synchronized boolean pull() {
        return tm.pull(this);
    }

    public synchronized Commit undo() {
        return tm.undo(this);
    }

    public synchronized Commit redo() {
        return tm.redo(this);
    }


    synchronized MutableObject getObjectSynchronizedIn(MutableObject dme, RootEntity dstRootEntity) {
        CommitId srcCommitId = getCurrentCommitId();
        CommitId dstCommitId = dstRootEntity.getCurrentCommitId();
        ObjectState state = tm.repositories.get(this).remote.getKey(dme);
        for (Commit commit : tm.commits.subMap(srcCommitId, false, dstCommitId, true).values()) {
            if (commit.getDeletionRecords().containsKey(state)) {
                return null;
            }
            //check if BEFORE exists in changeRecords
            if (commit.getChanges().containsKey(state)) {
                state = commit.getChanges().get(state);
            }
        }
        MutableObject result = tm.repositories.get(dstRootEntity).remote.get(state);
        if (result == null)
            throw new TransactionException("no matching object found in destination root entity for object", state.hashCode());
        return result;
    }

    private CommitId getCurrentCommitId() {
        if (!tm.repositories.containsKey(this))
            throw new NoTransactionsEnabledException();
        return tm.repositories.get(this).currentCommitId;
    }
}