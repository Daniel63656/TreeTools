package com.immersive.core;

import com.immersive.wrap.Wrapper;
import com.immersive.wrap.WrapperScope;

import java.util.HashMap;
import java.util.Map;

public abstract class DataModelEntity {
    final Map<WrapperScope, Wrapper<?>> registeredWrappers = new HashMap<>();

    public Map<WrapperScope, Wrapper<?>> getRegisteredWrappers() {
        return registeredWrappers;
    }

    abstract Class<?>[] getClassesOfConstructorParams();
    abstract Object[] getConstructorParamsAsKeys(LogicalObjectTree LOT);
    abstract DataModelEntity[] getConstructorParamsAsObjects();
    abstract RootEntity getRootEntity();
}