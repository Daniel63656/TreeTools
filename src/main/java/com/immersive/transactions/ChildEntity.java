package com.immersive.transactions;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for each class in the data model except the {@link RootEntity}. Each such class has one and only one owner
 * in the data model which can not be changed during the objects lifetime.
 * @param <O> class-type of the owner class
 */
public abstract class ChildEntity<O extends MutableObject> implements MutableObject {

    /**
     * cache reference to the root entity of the data model for direct access
     */
    private final RootEntity root;

    /**
     * reference to the owner of the class
     */
    protected final O owner;

    /**
     * prevent clearing to happen while object is already in the process of clearing. As member of this class, this field
     * is not part of the data model itself and is ignored by the transactional system and {@link JsonParser}
     */
    private boolean clearingInProgress;

    protected ChildEntity(O owner) {
        this.owner = owner;
        MutableObject it = owner;
        while(!(it instanceof RootEntity)) {
            it = ((ChildEntity<?>)it).getOwner();
        }
        root = (RootEntity) it;

        //log as creation if not in an ongoing pull
        Repository repository = TransactionManager.getInstance().repositories.get(getRootEntity());
        if (repository != null && !repository.ongoingPull) {
            repository.logLocalCreation(this);
        }
    }

    public O getOwner() {
        return owner;
    }

    /**
     * removes itself from the specific owner collection it is owned in.
     * This method needs to be implemented by any {@link ChildEntity}
     */
    protected abstract void destruct();

    /**
     * remove this object and all subsequent children from the data model. Not triggered by
     * {@link TransactionManager#pull(RootEntity)} because
     * pulling uses only the {@link ChildEntity#destruct()} method
     */
    public final void remove() {
        if (clearingInProgress)
            return;
        clearingInProgress = true;
        Repository repository = TransactionManager.getInstance().repositories.get(getRootEntity());
        if (repository != null)
            recursivelyDestruct(this, repository);
        else recursivelyDestruct(this);
    }
    private void recursivelyDestruct(ChildEntity<?> te) {
        te.destruct();
        for (ChildEntity<?> t : DataModelInfo.getChildren(te)) {
            recursivelyDestruct(t);
        }
    }
    private void recursivelyDestruct(ChildEntity<?> te, Repository repository) {
        repository.logLocalDeletion(te);    //log object as it was before possible modifications in destruct()
        te.destruct();
        for (ChildEntity<?> t : DataModelInfo.getChildren(te)) {
            recursivelyDestruct(t, repository);
        }
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
    public Class<?>[] constructorParameterTypes() {
        return new Class<?>[]{owner.getClass()};
    }
    @Override
    public Object[] constructorParameterStates(Remote remote) {
        return new Object[]{remote.getLogicalObjectKeyOfOwner(this)};
    }
    @Override
    public MutableObject[] constructorParameterMutables() {
        return new MutableObject[]{owner};
    }
    @Override
    public RootEntity getRootEntity() {
        return root;
    }

    @Override
    public MutableObject getCorrespondingObjectIn(RootEntity dstRootEntity) {
        return root.getObjectSynchronizedIn(this, dstRootEntity);
    }
}