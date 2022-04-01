package com.immersive.core;

public abstract class RootEntity extends DataModelEntity {
    @Override
    Class<?>[] getClassesOfConstructorParams() {
        return new Class<?>[0];
    }
    @Override
    Object[] getConstructorParamsAsKeys(LogicalObjectTree LOT) {
        return new Object[0];
    }
    @Override
    DataModelEntity[] getConstructorParamsAsObjects() { return new DataModelEntity[0];}
}