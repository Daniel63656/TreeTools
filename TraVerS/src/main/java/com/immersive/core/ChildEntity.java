package com.immersive.core;

public abstract class ChildEntity<O extends DataModelEntity> extends DataModelEntity {
    final O owner;

    protected ChildEntity(O owner) {
        this.owner = owner;
    }

    public O getOwner() {
        return owner;
    }

    @Override
    LogicalObjectKey[] getConstructorParams(LogicalObjectTree LOT) {
        LogicalObjectKey[] params = new LogicalObjectKey[1];
        params[0] = LOT.getLogicalObjectKeyOfOwner(this);
        return params;
    }
}
