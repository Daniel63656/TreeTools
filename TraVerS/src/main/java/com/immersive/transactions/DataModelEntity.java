package com.immersive.transactions;

import com.immersive.wrap.Wrapper;
import com.immersive.wrap.WrapperScope;

import java.util.Map;

public interface DataModelEntity {
    Map<WrapperScope, Wrapper<?>> getRegisteredWrappers();
    Class<?>[] getClassesOfConstructorParams();
    Object[] getConstructorParamsAsKeys(LogicalObjectTree LOT);
    DataModelEntity[] getConstructorParamsAsObjects();
    RootEntity getRootEntity();
    DataModelEntity getCorrespondingObjectIn(RootEntity dstRootEntity);
}