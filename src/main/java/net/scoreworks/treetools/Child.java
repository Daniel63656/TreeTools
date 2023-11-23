package net.scoreworks.treetools;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for each class in the data model except the {@link RootEntity}. Each such class has one and only one owner
 * in the data model which can not be changed during the object's lifetime.
 * @param <O> class-type of the owner class
 */
public abstract class Child<O extends MutableObject> implements MutableObject {

    /**
     * cache reference to the root entity of the data model for direct access
     */
    private final RootEntity root;

    /**
     * reference to the owner of the class
     */
    protected final O owner;

    /**
     * prevent removal to be triggered several times on the same object. As member of this class, this field
     * is not part of the data model itself and is ignored by the transactional system and {@link JsonParser}
     */
    private transient boolean removalInProcess;

    protected Child(O owner) {
        this.owner = owner;
        MutableObject it = owner;
        while(!(it instanceof RootEntity)) {
            it = ((Child<?>)it).getOwner();
        }
        root = (RootEntity) it;
        //notify wrappers of owner
        owner.notifyRegisteredWrappersAboutChange();
        //log as creation (isn't done by repository when in ongoing pull)
        Repository repository = TransactionManager.getInstance().repositories.get(getRootEntity());
        if (repository != null) {
            repository.logLocalCreation(this);
        }
    }

    public O getOwner() {
        return owner;
    }

    /**
     * remove this object and all subsequent children from the data model.
     * This method will go through all children recursively and call {@link Child#removeFromOwner()} and {@link Child#onRemove()}
     * on them, notify their registered wrappers about the removal and create a delta if a local {@link Repository} exists.
     * Apart from notifying all involved wrappers, this is also necessary to make this removal invertible
     */
    public final void remove() {
        if (removalInProcess)
            return;
        removalInProcess = true;
        //notify wrappers of owner about change (not done for subsequent children)
        owner.notifyRegisteredWrappersAboutChange();
        //start removing all subsequent children
        Repository repository = TransactionManager.getInstance().repositories.get(getRootEntity());
        if (repository != null)
            recursivelyRemove(this, repository);
        else recursivelyRemove(this);
    }
    private void recursivelyRemove(Child<?> te) {
        te.notifyAndRemoveRegisteredWrappers();
        te.removeFromOwner();
        te.onRemove();
        for (Child<?> t : DataModelInfo.getChildren(te)) {
            recursivelyRemove(t);
        }
    }
    private void recursivelyRemove(Child<?> te, Repository repository) {
        repository.logLocalDeletion(te);
        te.notifyAndRemoveRegisteredWrappers();
        te.removeFromOwner();
        te.onRemove();
        for (Child<?> t : DataModelInfo.getChildren(te)) {
            recursivelyRemove(t, repository);
        }
    }

    /**
     * remove itself from the owner's collection or field
     */
    protected abstract void removeFromOwner();

    /**
     * this method gets called when this object is being removed from the data model. In it the object
     * can take actions that remove any references to itself to leave an intact data model behind. This may include
     * removing other objects that are mapped with this as key, setting fields pointing to this object to null and other
     * data model specific clean-ups. Implementation is optional
     */
    protected void onRemove() {}

    @Override
    public List<Wrapper<?>> getRegisteredWrappers() {
        List<Wrapper<?>> wrappers = new ArrayList<>();
        for (WrapperScope scope : root.wrapperScopes) {
            if (scope.getRegisteredWrappers().containsKey(this))
                wrappers.add(scope.getRegisteredWrappers().get(this));
        }
        return wrappers;
    }

    @Override
    public Wrapper<?> getRegisteredWrapper(WrapperScope scope) {
        return scope.getRegisteredWrappers().get(this);
    }

    @Override
    public void notifyAndRemoveRegisteredWrappers() {
        for (WrapperScope scope : root.wrapperScopes) {
            if (scope.getRegisteredWrappers().containsKey(this)) {
                scope.getRegisteredWrappers().get(this).onWrappedRemoved();
                scope.getRegisteredWrappers().remove(this);
            }
        }
    }
    @Override
    public void notifyRegisteredWrappersAboutChange() {
        for (WrapperScope scope : root.wrapperScopes) {
            if (scope.getRegisteredWrappers().containsKey(this))
                scope.getRegisteredWrappers().get(this).onWrappedChanged();
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