package com.immersive.transactions;

import com.immersive.transactions.LogicalObjectTree.LogicalObjectKey;
import com.immersive.transactions.exceptions.TransactionException;
import com.immersive.wrap.Wrapper;
import com.immersive.wrap.WrapperScope;

import java.util.HashMap;
import java.util.Map;


/**
 * Transactional class for the root element of the data model. This class holds transactional
 * methods like push, pull, redo and undo are called via this object.
 */
public abstract class RootEntity implements DataModelEntity {
    private final TransactionManager tm = TransactionManager.getInstance();

    /**
     * list of subscribed wrapper objects that receive updates when object chages or gets deleted
     */
    private final Map<WrapperScope, Wrapper<?>> registeredWrappers = new HashMap<>();
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