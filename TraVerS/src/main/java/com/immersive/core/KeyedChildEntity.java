package com.immersive.core;

public abstract class KeyedChildEntity<O extends DataModelEntity, K> extends ChildEntity<O> {
    final K key;

    public KeyedChildEntity(O owner, K key) {
        super(owner);
        this.key = key;
    }

    public K getKey() {
        return key;
    }

    @Override
    Class<?>[] getClassesOfConstructorParams() {
        return new Class<?>[]{owner.getClass(), key.getClass()};
    }

    @Override
    Object[] getConstructorParams(LogicalObjectTree LOT) {
        if (key instanceof DataModelEntity)
            return new Object[]{LOT.getLogicalObjectKeyOfOwner(this), LOT.getKey(key)};
        else    //the key instance can be directly used for construction in other workcopy, because primitive wrapper classes are IMMUTABLE in Java
            return new Object[]{LOT.getLogicalObjectKeyOfOwner(this), key};
    }
}
