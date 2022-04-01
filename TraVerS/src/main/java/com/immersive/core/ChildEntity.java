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
    Class<?>[] getClassesOfConstructorParams() {
        return new Class<?>[]{owner.getClass()};
    }

    @Override
    Object[] getConstructorParams(LogicalObjectTree LOT) {
        return new Object[]{LOT.getLogicalObjectKeyOfOwner(this)};
    }
}