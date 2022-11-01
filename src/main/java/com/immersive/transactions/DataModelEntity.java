package com.immersive.transactions;

import com.immersive.wrap.Wrappable;

public interface DataModelEntity extends Wrappable {
    Class<?>[] getClassesOfConstructorParams();
    Object[] getConstructorParamsAsKeys(LogicalObjectTree LOT);
    DataModelEntity[] getConstructorParamsAsObjects();
    RootEntity getRootEntity();
    DataModelEntity getCorrespondingObjectIn(RootEntity dstRootEntity);
}