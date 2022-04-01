package com.immersive.core;

public abstract class RootEntity extends DataModelEntity {
    @Override
    Class<?>[] getClassesOfConstructorParams() {
        return new Class<?>[0];
    }

    @Override
    Object[] getConstructorParams(LogicalObjectTree LOT) {
        return null;
    }
}