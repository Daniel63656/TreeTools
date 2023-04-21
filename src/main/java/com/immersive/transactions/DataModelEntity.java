package com.immersive.transactions;

/**
 * Necessary methods that each class of the data model must have to make transactions possible
 */
public interface DataModelEntity extends Wrappable {
    Class<?>[] constructorParameterTypes();
    Object[] constructorParameterLOKs(LogicalObjectTree LOT);
    DataModelEntity[] constructorParameterObjects();
    RootEntity getRootEntity();
    DataModelEntity getCorrespondingObjectIn(RootEntity dstRootEntity);
}