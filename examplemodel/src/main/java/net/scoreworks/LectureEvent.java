package net.scoreworks;

import net.scoreworks.treetools.IndexedChild;
import net.scoreworks.treetools.annotations.TransactionalConstructor;

public class LectureEvent extends IndexedChild<Course> {

    @TransactionalConstructor
    public LectureEvent(Course owner, Integer index) {
        super(owner, index);
    }

    @Override
    protected void removeFromOwner() {
        getOwner().events[getIndex()] = null;
    }

    @Override
    protected void addToOwner() {
        getOwner().events[getIndex()] = this;
    }
}
