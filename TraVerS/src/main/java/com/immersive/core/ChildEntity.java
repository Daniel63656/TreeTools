package com.immersive.core;

public abstract class ChildEntity<O extends DataModelEntity> extends DataModelEntity {
    final RootEntity root;
    final O owner;

    protected ChildEntity(O owner) {
        this.owner = owner;
        DataModelEntity it = owner;
        while(!(it instanceof RootEntity)) {
            it = ((ChildEntity<?>)it).getOwner();
        }
        root = (RootEntity) it;
    }

    public O getOwner() {
        return owner;
    }

    @Override
    Class<?>[] getClassesOfConstructorParams() {
        return new Class<?>[]{owner.getClass()};
    }
    @Override
    Object[] getConstructorParamsAsKeys(LogicalObjectTree LOT) {
        return new Object[]{LOT.getLogicalObjectKeyOfOwner(this)};
    }
    @Override
    DataModelEntity[] getConstructorParamsAsObjects() {
        return new DataModelEntity[]{owner};
    }
    @Override
    RootEntity getRootEntity() {
        return root;
    }
}