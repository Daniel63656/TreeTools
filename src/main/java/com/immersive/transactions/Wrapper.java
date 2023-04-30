package com.immersive.transactions;

/**
 * Abstract base class for a wrapper. Gets notified when the wrapped object gets deleted or changed.
 * THis objects lifetime is linked to an associated {@link WrapperScope}.
 */
public abstract class Wrapper<WO extends MutableObject> {
    protected final WrapperScope wrapperScope;
    protected final WO wrapped;

    public Wrapper(WrapperScope wrapperScope, WO wrapped) {
        this.wrapperScope = wrapperScope;
        this.wrapped = wrapped;
        wrapperScope.registeredWrappers.put(wrapped, this);
    }

    public WrapperScope getWrapperScope() {
        return wrapperScope;
    }

    public WO getWrapped() {
        return wrapped;
    }

    public void remove() {
        wrapperScope.registeredWrappers.remove(wrapped);
    }

    //Override these methods to implement wrapper specific behaviour

    /**
     * is invoked when the wrapped object is removed from the data model
     */
    public void onWrappedRemoved() {
    }

    /**
     * is invoked each time the wrapped object is changed or a child is added/removed
     */
    public void onWrappedChanged() {}
}