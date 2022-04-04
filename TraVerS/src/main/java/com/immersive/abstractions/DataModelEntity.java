package com.immersive.abstractions;

import com.immersive.core.LogicalObjectTree;
import com.immersive.wrap.Wrapper;
import com.immersive.wrap.WrapperScope;

import java.util.Map;

public interface DataModelEntity {
    //final Map<WrapperScope, Wrapper<?>> registeredWrappers = new HashMap<>();

    public Map<WrapperScope, Wrapper<?>> getRegisteredWrappers();
    Class<?>[] getClassesOfConstructorParams();
    abstract Object[] getConstructorParamsAsKeys(LogicalObjectTree LOT);
    abstract DataModelEntity[] getConstructorParamsAsObjects();
    abstract RootEntity getRootEntity();
}