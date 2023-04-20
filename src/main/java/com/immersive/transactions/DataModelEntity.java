package com.immersive.transactions;

/**
 * Necessary methods that each class of the data model must have to make transactions possible
 */
public interface DataModelEntity extends Wrappable {
    Class<?>[] getClassesOfConstructorParams();
    Object[] getConstructorParamsAsKeys(LogicalObjectTree LOT);
    DataModelEntity[] getConstructorParamsAsObjects();
    RootEntity getRootEntity();
    DataModelEntity getCorrespondingObjectIn(RootEntity dstRootEntity);
}