package com.immersive.core;


public abstract class DataModelEntity {

    abstract Class<?>[] getClassesOfConstructorParams();

    abstract Object[] getConstructorParams(LogicalObjectTree LOT);
}