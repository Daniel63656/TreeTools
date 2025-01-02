package net.scoreworks.examplemodel;

import net.scoreworks.treetools.annotations.TransactionalConstructor;

public final class Time {
    public final int hour;
    public final int minute;

    public Time(int hour, int minute) {
        this.hour = hour;
        this.minute = minute;
    }

    // Used by the transactional system. Fields will be set by reflections.
    @TransactionalConstructor
    private Time() {
        this.hour = 0;
        this.minute = 0;
    }
}
