package com.immersive.transactions;

/**
 * Abstract base class for a wrapper. Gets notified when the wrapped object gets deleted or changed. Its lifetime
 * is linked to an associated {@link WrapperScope}.
 */
public abstract class Wrapper<WO extends DataModelEntity> {
    public WO wrapped;

    public Wrapper(WrapperScope ws, WO wrapped) {
        this.wrapped = wrapped;
        ws.registerWrapper(this);
    }

    //Override these methods to implement wrapper specific behaviour

    public void onWrappedCleared() {}

    public void onWrappedChanged() {}
}