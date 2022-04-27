package com.immersive.test_model;

public final class Foo {
    //static, non final members can exist!
    static String string = "A String";
    final Integer f;
    final Fo fo;

    //order matters
    public Foo(int f, Fo fo) {
        this.f = f;
        this.fo = fo;
    }

    public Foo() {
        f = null;
        fo = null;
    }


}
