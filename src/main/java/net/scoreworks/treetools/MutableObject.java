/*
 * Copyright (c) 2023 Daniel Maier.
 * Licensed under the MIT License.
 */

package net.scoreworks.treetools;

/**
 * Base interface for any mutable data model class. Defines utility functions needed to make transactions possible
 */
public interface MutableObject extends Wrappable {

    /**
     * @return an array of all types that are used in the transactional constructor
     */
    Class<?>[] constructorParameterTypes();

    /**
     * @param remote access to object states
     * @return an array of object {@link Remote.ObjectState}s used in the transactional constructor.
     * if object is an {@link MutableObject}, otherwise the mutable object is its own state
     */
    Object[] constructorParameterStates(Remote remote);

    /**
     * @return an array of all objects used in the transactional constructor
     */
    Object[] constructorParameterObjects();

    /**
     * @return the root of the data model
     */
    RootEntity getRootEntity();

    /**
     * @return the corresponding object in another {@link RootEntity}
     */
    MutableObject getCorrespondingObjectIn(RootEntity dstRootEntity);
}
