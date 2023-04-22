package com.immersive.transactions;

/**
 * Child entity that is owned with two keys of the same type. Typical applications may be classes that
 * represent ranges of some sort.
 * @param <O> class-type of the owner class
 * @param <K> class-type of the keys used to own this class
 */
public abstract class DoubleKeyedChildEntity<O extends DataModelEntity, K> extends KeyedChildEntity<O, K> {

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
    public Object[] constructorParameterLOKs(LogicalObjectTree LOT) {
        if (key instanceof DataModelEntity)
            return new Object[]{LOT.getLogicalObjectKeyOfOwner(this), LOT.getKey(key), LOT.getKey(endKey)};
        else    //the key instance can be directly used for construction in other workcopy, because primitive wrapper classes are IMMUTABLE in Java
            return new Object[]{LOT.getLogicalObjectKeyOfOwner(this), key, endKey};
    }
    @Override
    public DataModelEntity[] constructorParameterDMEs() {
        if (key instanceof DataModelEntity)
            return new DataModelEntity[]{owner, (DataModelEntity) key, (DataModelEntity) endKey};
        else
            return new DataModelEntity[]{owner};
    }
}
