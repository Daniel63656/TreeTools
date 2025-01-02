package net.scoreworks.testmodel;

import net.scoreworks.treetools.annotations.TransactionalConstructor;

public final class Fo {
    final int f;
    final Integer F;

    public Fo(int f, Integer f1) {
        this.f = f;
        F = f1;
    }

    @TransactionalConstructor
    private Fo() {
        f = 0;
        F = null;
    }
}
