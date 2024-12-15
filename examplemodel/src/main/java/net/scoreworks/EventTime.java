package net.scoreworks;

import net.scoreworks.treetools.annotations.TransactionalConstructor;

public final class EventTime {
    static String TIMEZONE = "UTC";     //non-final but static fields are allowed as they are ignored by the transactional system.
    final Time start;
    final Time end;

    public EventTime(final Time start, final Time end) {
        this.start = start;
        this.end = end;
    }

    // Used by the transactional system. Fields will be set by reflections.
    @TransactionalConstructor
    private EventTime() {
        this.start = null;
        this.end = null;
    }
}
