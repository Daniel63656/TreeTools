package net.scoreworks.test_model;

public final class Fo {
    final int f;
    final Integer F;

    public Fo(int f, Integer f1) {
        this.f = f;
        F = f1;
    }

    //transactional constructor
    private Fo() {
        f = 0;
        F = null;
    }
}