package com.immersive.transactions;

import com.immersive.wrap.Wrapper;
import com.immersive.wrap.WrapperScope;

import java.util.HashMap;
import java.util.Map;

public abstract class ChildEntity<O extends DataModelEntity> implements DataModelEntity {
    final Map<WrapperScope, Wrapper<?>> registeredWrappers = new HashMap<>();
    @Override
    public Map<WrapperScope, Wrapper<?>> getRegisteredWrappers() {
        return registeredWrappers;
    }
    private boolean clearingInProgress;
    final RootEntity root;
    final O owner;

    protected ChildEntity(O owner) {
        this.owner = owner;
        DataModelEntity it = owner;
        while(!(it instanceof RootEntity)) {
            it = ((ChildEntity<?>)it).getOwner();
        }
        root = (RootEntity) it;
    }

    public O getOwner() {
        return owner;
    }

    public boolean clear() {
        if (clearingInProgress)
            return true;
        clearingInProgress = true;
        TransactionManager.destruct(this);
        for (Wrapper<?> wrapper : getRegisteredWrappers().values()) {
            wrapper.onWrappedCleared();
        }
        return false;
    }

    @Override
    public Class<?>[] getClassesOfConstructorParams() {
        return new Class<?>[]{owner.getClass()};
    }
    @Override
    public Object[] getConstructorParamsAsKeys(LogicalObjectTree LOT) {
        return new Object[]{LOT.getLogicalObjectKeyOfOwner(this)};
    }
    @Override
    public DataModelEntity[] getConstructorParamsAsObjects() {
        return new DataModelEntity[]{owner};
    }
    @Override
    public RootEntity getRootEntity() {
        return root;
    }

    @Override
    public DataModelEntity getCorrespondingObjectIn(RootEntity dstRootEntity) {
        return root.getObjectSynchronizedIn(this, dstRootEntity);
    }
}