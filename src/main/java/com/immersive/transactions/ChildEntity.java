package com.immersive.transactions;

import java.util.ArrayList;
import java.util.List;

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
     * prevent clearing to happen while object is in the process of clearing. As member of this class, this field
     * is not part of the data model itself and is ignored by the transactional system and {@link JsonParser}
     */
    private boolean clearingInProgress;

    protected ChildEntity(O owner) {
        this.owner = owner;
        DataModelEntity it = owner;
        while(!(it instanceof RootEntity)) {
            it = ((ChildEntity<?>)it).getOwner();
        }
        root = (RootEntity) it;

        //log as creation if not in an ongoing pull
        Workcopy workcopy = TransactionManager.getInstance().workcopies.get(getRootEntity());
        if (workcopy != null && !workcopy.ongoingPull) {
            /*if (!workcopy.locallyChangedOrCreated.contains(this))
                System.out.println(getClass().getSimpleName()+" got created");*/
            workcopy.locallyCreatedOrChanged.add(this);
        }
    }

    public O getOwner() {
        return owner;
    }

    protected abstract void destruct();

    //not triggered by pulling because pulling uses destruct() only
    public boolean clear() {
        if (clearingInProgress)
            return true;
        clearingInProgress = true;
        destruct();
        Workcopy workcopy = TransactionManager.getInstance().workcopies.get(getRootEntity());
        if (workcopy != null) {
            workcopy.locallyDeleted.add(this);
            //System.out.println(te.getClass().getSimpleName() + " got deleted");
        }
        onCleared();
        return false;
    }

    @Override
    public List<Wrapper<?>> getRegisteredWrappers() {
        List<Wrapper<?>> wrappers = new ArrayList<>();
        for (WrapperScope scope : root.wrapperScopes) {
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
        for (WrapperScope scope : root.wrapperScopes) {
            if (scope.registeredWrappers.containsKey(this))
                scope.registeredWrappers.get(this).onWrappedCleared();
        }
    }
    @Override
    public void onChanged() {
        for (WrapperScope scope : root.wrapperScopes) {
            if (scope.registeredWrappers.containsKey(this))
                scope.registeredWrappers.get(this).onWrappedChanged();
        }
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