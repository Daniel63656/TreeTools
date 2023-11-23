package net.scoreworks.treetools;

/**
 * Child entity that is owned in a map.
 * @param <O> class-type of the owner class
 * @param <K> class-type of the key used to own this class
 */
public abstract class MappedChild<O extends MutableObject, K> extends Child<O> {

    /**
     * reference to the key the object is saved with
     */
    private K key;

    public MappedChild(O owner, K key) {
        super(owner);
        this.key = key;
    }

    public K getKey() {
        return key;
    }

    /**
     * change the key of a MappedChild without destroying the object
     */
    @SuppressWarnings("unchecked")
    public void migrate(Object newKey) {
        removeFromOwner();
        key = (K) newKey;
        addToOwner();
    }

    @Override
    public Class<?>[] constructorParameterTypes() {
        return new Class<?>[]{getOwner().getClass(), key.getClass()};
    }
    @Override
    public Object[] constructorParameterStates(Remote remote) {
        if (key instanceof MutableObject)
            return new Object[]{remote.getLogicalObjectKeyOfOwner(this), remote.getKey(key)};
        else    //any immutable object act as their own state in the transactional system
            return new Object[]{remote.getLogicalObjectKeyOfOwner(this), key};
    }
    @Override
    public Object[] constructorParameterObjects() {
        return new Object[]{getOwner(), key};
    }
}