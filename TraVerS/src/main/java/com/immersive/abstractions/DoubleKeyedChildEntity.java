package com.immersive.abstractions;

import com.immersive.core.LogicalObjectTree;

public abstract class DoubleKeyedChildEntity<O extends DataModelEntity, K> extends KeyedChildEntity<O, K> {
    final K endKey;

    public DoubleKeyedChildEntity(O owner, K startKey, K endKey) {
        super(owner, startKey);
        this.endKey = endKey;
    }

    public K getEndKey() {
        return endKey;
    }

    @Override
    public Class<?>[] getClassesOfConstructorParams() {
        return new Class<?>[]{owner.getClass(), key.getClass(), key.getClass()};
    }
    @Override
    public Object[] getConstructorParamsAsKeys(LogicalObjectTree LOT) {
        if (key instanceof DataModelEntity)
            return new Object[]{LOT.getLogicalObjectKeyOfOwner(this), LOT.getKey(key), LOT.getKey(endKey)};
        else    //the key instance can be directly used for construction in other workcopy, because primitive wrapper classes are IMMUTABLE in Java
            return new Object[]{LOT.getLogicalObjectKeyOfOwner(this), key, endKey};
    }
    @Override
    public DataModelEntity[] getConstructorParamsAsObjects() {
        if (key instanceof DataModelEntity)
            return new DataModelEntity[]{owner, (DataModelEntity) key, (DataModelEntity) endKey};
        else
            return new DataModelEntity[]{owner};
    }
}
