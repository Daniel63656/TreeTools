package com.immersive.transactions;

import com.immersive.wrap.Wrapper;
import com.immersive.wrap.WrapperScope;

import java.util.HashMap;
import java.util.Map;

public abstract class RootEntity implements DataModelEntity {
    final Map<WrapperScope, Wrapper<?>> registeredWrappers = new HashMap<>();
    @Override
    public Map<WrapperScope, Wrapper<?>> getRegisteredWrappers() {
        return registeredWrappers;
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

    public synchronized void commit() {
        TransactionManager.getInstance().commit(this);
    }
    public synchronized boolean pull() {
        return TransactionManager.getInstance().pull(this);
    }

    public synchronized DataModelEntity getCorrespondingObjectIn(DataModelEntity dme, RootEntity dstRootEntity) {
        if (dme.getRootEntity() != this)
            throw new RuntimeException("tried to call this method from a RootEntity not possessing the specified DataModelEntity!");
        TransactionManager tm = TransactionManager.getInstance();
        RootEntity srcRootEntity = dme.getRootEntity();
        CommitId srcCommitId = tm.getCurrentCommitId(srcRootEntity);
        CommitId dstCommitId = tm.getCurrentCommitId(dstRootEntity);
        LogicalObjectKey LOK = tm.workcopies.get(srcRootEntity).LOT.getKey(dme);
        for (Commit commit : tm.commits.subMap(srcCommitId, false, dstCommitId, true).values()) {
            if (commit.deletionRecords.containsKey(LOK)) {
                return null;
            }
            if (commit.changeRecords.containsKey(LOK)) {
                LOK = commit.changeRecords.get(LOK);
            }
        }
        DataModelEntity result = tm.workcopies.get(dstRootEntity).LOT.get(LOK);
        if (result == null)
            throw new RuntimeException("object mapping FAILED: couldn't find object by LOK in source RootEntity");
        return result;
    }
}