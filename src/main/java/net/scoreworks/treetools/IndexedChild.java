/*
 * Copyright (c) 2023 Daniel Maier.
 * Licensed under the MIT License.
 */

package net.scoreworks.treetools;

/**
 * Child entity that is owned by a final index (e.g. in an array). Lists are not supported, as they have non-final
 * indices by definition.
 * @param <O> class-type of the owner class
 */
public abstract class IndexedChild<O extends MutableObject> extends Child<O> {

    /**
     * Reference to the key the object is saved with
     */
    private final Integer index;

    public IndexedChild(O owner, Integer index) {
        super(owner);
        this.index = index;
        addToOwner();   // now index can be used
    }

    public int getIndex() {
        return index;
    }

    @Override
    protected boolean isDirectChild() {
        return false;
    }

    @Override
    public Class<?>[] constructorParameterTypes() {
        return new Class<?>[]{getOwner().getClass(), int.class};
    }
    @Override
    public Object[] constructorParameterStates(Remote remote) {
        return new Object[]{remote.getLogicalObjectKeyOfOwner(this), index};
    }
    @Override
    public Object[] constructorParameterObjects() {
        return new Object[]{getOwner(), index};
    }
}
