package net.scoreworks;

import net.scoreworks.treetools.Child;
import net.scoreworks.treetools.annotations.TransactionalConstructor;

import java.util.HashMap;
import java.util.Map;

public class Course extends Child<Department> {
    LectureEvent[] events = new LectureEvent[7];    // To demonstrate the usage of arrays
    String name;
    SemesterType semesterType;

    // This field does not exist as far as the transactional system is concerned
    transient Map<Integer, Student> students = new HashMap<>();

    public Course(Department department, String name, SemesterType semesterType) {
        super(department);
        this.name = name;
        this.semesterType = semesterType;
    }

    @TransactionalConstructor
    protected Course(Department owner) {
        super(owner);
    }

    @Override
    protected void removeFromOwner() {
        getOwner().courses.remove(this);
    }

    @Override
    protected void addToOwner() {
        getOwner().courses.add(this);
    }
}
