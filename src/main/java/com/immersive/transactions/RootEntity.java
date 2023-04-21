package com.immersive.transactions;

import com.immersive.transactions.LogicalObjectTree.LogicalObjectKey;
import com.immersive.transactions.exceptions.TransactionException;

import java.util.*;


/**
 * Transactional class for the root element of the data model. This class holds transactional
 * methods like push, pull, redo and undo are called via this object.
 */
public abstract class RootEntity implements DataModelEntity {
    private final TransactionManager tm = TransactionManager.getInstance();

    /**
     * Set, holding all wrapper scopes. As member of this class, this field
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
        //this is never called since RootEntity has no clear() function
        throw new RuntimeException("Can't clear the RootEntity of a data model!");
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
    public Object[] constructorParameterLOKs(LogicalObjectTree LOT) {
        return new Object[0];
    }

    @Override
    public DataModelEntity[] constructorParameterObjects() { return new DataModelEntity[0];}

    @Override
    public RootEntity getRootEntity() {
        return this;
    }

    @Override
    public DataModelEntity getCorrespondingObjectIn(RootEntity dstRootEntity) {
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


    synchronized DataModelEntity getObjectSynchronizedIn(DataModelEntity dme, RootEntity dstRootEntity) {
        CommitId srcCommitId = tm.getCurrentCommitId(this);
        CommitId dstCommitId = tm.getCurrentCommitId(dstRootEntity);
        LogicalObjectKey LOK = tm.workcopies.get(this).LOT.getKey(dme);
        for (Commit commit : tm.commits.subMap(srcCommitId, false, dstCommitId, true).values()) {
            if (commit.deletionRecords.containsKey(LOK)) {
                return null;
            }
            //check if BEFORE exists in changeRecords
            if (commit.changeRecords.containsKey(LOK)) {
                LOK = commit.changeRecords.get(LOK);
            }
        }
        DataModelEntity result = tm.workcopies.get(dstRootEntity).LOT.get(LOK);
        if (result == null)
            throw new TransactionException("object mapping FAILED: couldn't find object in source RootEntity", LOK.hashCode());
        return result;
    }
}