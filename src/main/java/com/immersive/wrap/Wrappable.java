package com.immersive.wrap;

import java.util.Map;

public interface Wrappable {
    Map<WrapperScope, Wrapper<?>> getRegisteredWrappers();
    void onWrappedCleared();
    void onWrappedChanged();
}
