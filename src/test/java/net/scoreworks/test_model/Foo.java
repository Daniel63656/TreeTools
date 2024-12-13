package net.scoreworks.test_model;

public final class Foo extends FooParent {
    //static, non-final members can exist!
    static String string = "A String";
    final Integer f;

    //can have other non MutableObjects if they are also immutable and have the right constructor
    final Fo fo;


    public Foo(Fo fo, int f) {
        super(0);
        this.f = f;
        this.fo = fo;
    }

    public Foo(Fo fo, int f, int parent) {
        super(parent);
        this.f = f;
        this.fo = fo;
    }

    //transactional constructor
    private Foo() {
        super(0);
        f = null;
        fo = null;
    }


}
