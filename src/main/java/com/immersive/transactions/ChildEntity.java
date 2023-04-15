package com.immersive.transactions;

import com.immersive.wrap.Wrapper;
import com.immersive.wrap.WrapperScope;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for each class in the data model except the root element. Each such class has an owner in the data model
 * @param <O> class-type of the owner class
 */
public abstract class ChildEntity<O extends DataModelEntity> implements DataModelEntity {

    /**
     * cache reference to the root entity of the data model for direct access
     */
    private final RootEntity root;

    /**
     * reference to the owner of the class
     */
    protected final O owner;

    /**
     * prevent clearing to happen while object is in the process of clearing
     */
    private boolean clearingInProgress;

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