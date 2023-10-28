package net.scoreworks.treetools;


import java.util.List;

/**
 * Specifies methods a wrappable object must have to send notifications to its registered {@link Wrapper}s.
 */
public interface Wrappable {

    List<Wrapper<?>> getRegisteredWrappers();

    Wrapper<?> getRegisteredWrapper(WrapperScope scope);

    /**
     * Notify registered wrappers about the deletion of itself
     */
    void notifyAndRemoveRegisteredWrappers();

    /**
     * Notify registered wrappers about a change
     */
    void notifyRegisteredWrappersAboutChange();
}
