package com.immersive.abstractions;

import com.immersive.core.LogicalObjectTree;
import com.immersive.wrap.Wrapper;
import com.immersive.wrap.WrapperScope;

import java.util.Map;

public interface DataModelEntity {
    Map<WrapperScope, Wrapper<?>> getRegisteredWrappers();
    Class<?>[] getClassesOfConstructorParams();
    Object[] getConstructorParamsAsKeys(LogicalObjectTree LOT);
    DataModelEntity[] getConstructorParamsAsObjects();
    RootEntity getRootEntity();
}