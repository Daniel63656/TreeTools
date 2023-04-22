package com.immersive.transactions;

/**
 * Child entity that is owned with two keys of the same type. Typical applications may be classes that
 * represent ranges of some sort.
 * @param <O> class-type of the owner class
 * @param <K> class-type of the keys used to own this class
 */
public abstract class DoubleKeyedChildEntity<O extends MutableObject, K> extends KeyedChildEntity<O, K> {

    /**
     * reference to the second key the object is associated with
     */
    private final K endKey;

    public DoubleKeyedChildEntity(O owner, K startKey, K endKey) {
        super(owner, startKey);
        this.endKey = endKey;
    }

    public K getEndKey() {
        return endKey;
    }

    @Override
    public Class<?>[] constructorParameterTypes() {
        return new Class<?>[]{owner.getClass(), key.getClass(), key.getClass()};
    }
    @Override
    public Object[] constructorParameterStates(Remote remote) {
        if (key instanceof MutableObject)
            return new Object[]{remote.getLogicalObjectKeyOfOwner(this), remote.getKey(key), remote.getKey(endKey)};
        else    //any immutable object act as their own state in the transactional system
            return new Object[]{remote.getLogicalObjectKeyOfOwner(this), key, endKey};
    }
    @Override
    public MutableObject[] constructorParameterMutables() {
        if (key instanceof MutableObject)
            return new MutableObject[]{owner, (MutableObject) key, (MutableObject) endKey};
        else
            return new MutableObject[]{owner};
    }
}
