package com.immersive.transactions;

/**
 * Child entity that is owned in a map.
 * @param <O> class-type of the owner class
 * @param <K> class-type of the key used to own this class
 */
public abstract class KeyedChildEntity<O extends DataModelEntity, K> extends ChildEntity<O> {

    /**
     * reference to the key the object is saved with
     */
    protected final K key;

    public KeyedChildEntity(O owner, K key) {
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
    public Object[] constructorParameterLOKs(LogicalObjectTree LOT) {
        if (key instanceof DataModelEntity)
            return new Object[]{LOT.getLogicalObjectKeyOfOwner(this), LOT.getKey(key)};
        else    //the key instance can be directly used for construction in other workcopy, because primitive wrapper classes are IMMUTABLE in Java
            return new Object[]{LOT.getLogicalObjectKeyOfOwner(this), key};
    }
    @Override
    public DataModelEntity[] constructorParameterDMEs() {
        if (key instanceof DataModelEntity)
            return new DataModelEntity[]{owner, (DataModelEntity) key};
        else
            return new DataModelEntity[]{owner};
    }
}