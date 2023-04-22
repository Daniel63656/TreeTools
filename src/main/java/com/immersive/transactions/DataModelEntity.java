package com.immersive.transactions;

/**
 * Necessary methods that each class of the data model must have to make transactions possible
 */
public interface DataModelEntity extends Wrappable {
    Class<?>[] constructorParameterTypes();

    //LOK when param is a DME, otherwise the immutable object itself
    Object[] constructorParameterLOKs(LogicalObjectTree LOT);

    //all DataModelEntities. Immutable objects are omitted here
    DataModelEntity[] constructorParameterDMEs();
    RootEntity getRootEntity();
    DataModelEntity getCorrespondingObjectIn(RootEntity dstRootEntity);
}