package com.immersive.wrap;

import com.immersive.transactions.DataModelEntity;

public class Wrapper<WO extends DataModelEntity> {
  public WO wrapped;

  public Wrapper(WrapperScope ws, WO wrapped) {
    this.wrapped = wrapped;
    this.wrapped.getRegisteredWrappers().put(ws, this);
  }

  public void onWrappedCleared() {}
  public void onDataChange() {}
}