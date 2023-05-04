package com.immersive.transactions;

import java.util.Map;

/**
 * Acts as a "category" for wrappers and provides direct access to all wrappers of this scope.
 * The concrete class must save the wrappers, mapped by their wrapped {@link MutableObject}.
 * Scopes get added to a data models' {@link RootEntity} and removing the scope gets rid of all wrappers without the need for clean up.
 */
public interface WrapperScope {

    Map<MutableObject, Wrapper<?>> getRegisteredWrappers();

}