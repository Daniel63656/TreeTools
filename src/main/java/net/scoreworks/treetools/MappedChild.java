package net.scoreworks.treetools;

import java.util.Map;

/**
 * Child entity that is owned in a map.
 * @param <O> class-type of the owner class
 * @param <K> class-type of the key used to own this class
 */
public abstract class MappedChild<O extends MutableObject, K> extends Child<O> {

    /**
     * reference to the key the object is saved with
     */
    protected final K key;

    public MappedChild(O owner, K key) {
        super(owner);
        this.key = key;
    }

    public K getKey() {
        return key;
    }

    @Override
    public Class<?>[] constructorParameterTypes() {
        return new Class<?>[]{owner.getClass(), key.getClass()};
    }
    @Override
    public Object[] constructorParameterStates(Remote remote) {
        if (key instanceof MutableObject)
            return new Object[]{remote.getLogicalObjectKeyOfOwner(this), remote.getKey(key)};
        else    //any immutable object act as their own state in the transactional system
            return new Object[]{remote.getLogicalObjectKeyOfOwner(this), key};
    }
    @Override
    public MutableObject[] constructorParameterMutables() {
        if (key instanceof MutableObject)
            return new MutableObject[]{owner, (MutableObject) key};
        else
            return new MutableObject[]{owner};
    }
}