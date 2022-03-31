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
    LogicalObjectKey[] getConstructorParams(LogicalObjectTree LOT) {
        LogicalObjectKey[] params = new LogicalObjectKey[2];
        params[0] = LOT.getLogicalObjectKeyOfOwner(this);
        params[1] = LOT.getKey(key);
        params[1] =
        return params;
    }
}
