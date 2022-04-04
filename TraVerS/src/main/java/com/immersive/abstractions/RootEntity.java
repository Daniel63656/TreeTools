package com.immersive.abstractions;

import com.immersive.core.LogicalObjectTree;
import com.immersive.wrap.Wrapper;
import com.immersive.wrap.WrapperScope;

import java.util.HashMap;
import java.util.Map;

public abstract class RootEntity implements DataModelEntity {
    final Map<WrapperScope, Wrapper<?>> registeredWrappers = new HashMap<>();
    @Override
    public Map<WrapperScope, Wrapper<?>> getRegisteredWrappers() {
        return registeredWrappers;
    }

    @Override
    public Class<?>[] getClassesOfConstructorParams() {
        return new Class<?>[0];
    }
    @Override
    public Object[] getConstructorParamsAsKeys(LogicalObjectTree LOT) {
        return new Object[0];
    }

    @Override
    public DataModelEntity[] getConstructorParamsAsObjects() { return new DataModelEntity[0];}

    @Override
    public RootEntity getRootEntity() {
        return this;
    }
}