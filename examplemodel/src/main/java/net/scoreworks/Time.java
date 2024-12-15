package net.scoreworks;

import net.scoreworks.treetools.annotations.TransactionalConstructor;

public final class Time {
    final int hour;
    final int minute;

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
