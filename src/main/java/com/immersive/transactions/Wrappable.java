package com.immersive.transactions;


import java.util.List;

/**
 * Specifies methods a wrappable object must have.
 */
public interface Wrappable {

    List<Wrapper<?>> getRegisteredWrappers();

    Wrapper<?> getRegisteredWrapper(WrapperScope scope);

    /**
     * Notify registered wrappers about the deletion of itself
     */
    void onCleared();

    /**
     * Notify registered wrappers about a change
     */
    void onChanged();
}
