package net.scoreworks.treetools;

/**
 * Necessary methods that each mutable class of the data model must have to make transactions possible
 */
public interface MutableObject extends Wrappable {

    /**
     * @return an array of all types that are used in the transactional constructor
     */
    Class<?>[] constructorParameterTypes();

    /**
     * @param remote access to object states
     * @return an array of object states used in the transactional constructor. Of type {@link Remote.ObjectState}
     * if object is an {@link MutableObject}, otherwise the mutable object is its own state
     */
    Object[] constructorParameterStates(Remote remote);

    /**
     * @return an array of all {@link MutableObject}s used in the transactional constructor. Any immutable objects
     * are omitted here
     */
    MutableObject[] constructorParameterMutables();

    /**
     * @return the root of the data model
     */
    RootEntity getRootEntity();

    /**
     * @return the corresponding object in another {@link RootEntity}
     */
    MutableObject getCorrespondingObjectIn(RootEntity dstRootEntity);
}