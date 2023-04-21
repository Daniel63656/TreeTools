package com.immersive.transactions;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


/**
 * Acts as a "category" for wrappers. Provides direct access to all wrappers of the same scope. Scopes get added to
 * a data models' {@link RootEntity} and removing the scope gets rid of all wrappers without the need for clean up.
 */
public class WrapperScope {
    protected final Map<DataModelEntity, Wrapper<?>> registeredWrappers = new HashMap<>();

    public Collection<Wrapper<?>> getRegisteredWrappers() {
        return Collections.unmodifiableCollection(registeredWrappers.values());
    }

    public Wrapper<?> getWrapper(DataModelEntity dme) {
        return registeredWrappers.get(dme);
    }

    //add wrappers by constructing them!

    public Wrapper<?> removeWrapper(Wrapper<?> wrapper) {
        return registeredWrappers.remove(wrapper.wrapped);
    }

    public Wrapper<?> removeWrapperFor(DataModelEntity dme) {
        return registeredWrappers.remove(dme);
    }

    public boolean isEmpty() {
        return registeredWrappers.isEmpty();
    }

    public boolean contains(DataModelEntity dme) {
        return registeredWrappers.containsKey(dme);
    }

    public int size() {
        return registeredWrappers.size();
    }

    public void clear() {
        registeredWrappers.clear();
    }
}