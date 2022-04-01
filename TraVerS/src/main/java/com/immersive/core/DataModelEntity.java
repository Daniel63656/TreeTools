package com.immersive.core;


public abstract class DataModelEntity {

    abstract Class<?>[] getClassesOfConstructorParams();
    abstract Object[] getConstructorParamsAsKeys(LogicalObjectTree LOT);
    abstract DataModelEntity[] getConstructorParamsAsObjects();
}