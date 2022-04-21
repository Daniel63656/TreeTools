package com.immersive.transactions;

import com.immersive.transactions.exceptions.TransactionException;
import com.immersive.wrap.Wrapper;
import com.immersive.wrap.WrapperScope;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public abstract class RootEntity implements DataModelEntity {
    TransactionManager tm = TransactionManager.getInstance();
    final Map<WrapperScope, Wrapper<?>> registeredWrappers = new HashMap<>();
    @Override
    public Map<WrapperScope, Wrapper<?>> getRegisteredWrappers() {
        return registeredWrappers;
    }
    @Override
    public void onWrappedCleared() {
        for (Wrapper<?> w : registeredWrappers.values()) {
            w.onWrappedCleared();
        }
    }
    @Override
    public void onWrappedChanged() {
        registeredWrappers.values().removeIf(Wrapper::onWrappedChanged);
    }

    @Override
    public Class<?>[] getClassesOfConstructorParams() {
        return new Class<?>[0];
    }
    @Override
    public Object[] getConstructorParamsAsKeys(LogicalObjectTree LOT) {
        return new Object[0];
    }

    @Override
    public DataModelEntity[] getConstructorParamsAsObjects() { return new DataModelEntity[0];}

    @Override
    public RootEntity getRootEntity() {
        return this;
    }

    @Override
    public DataModelEntity getCorrespondingObjectIn(RootEntity dstRootEntity) {
        return getObjectSynchronizedIn(this, dstRootEntity);
    }

    public synchronized Commit commit() {
        return TransactionManager.getInstance().commit(this);
    }
    public synchronized Commit undo() {
        return tm.undo(this);
    }
    public synchronized Commit redo() {
        return tm.redo(this);
    }
    public synchronized boolean pull() {
        return TransactionManager.getInstance().pull(this);
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