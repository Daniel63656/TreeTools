package net.scoreworks;

import net.scoreworks.treetools.Child;
import net.scoreworks.treetools.annotations.TransactionalConstructor;

import java.util.HashSet;
import java.util.Set;

public class Department extends Child<University> {
    Set<Course> courses = new HashSet<>();
    String name;

    // This is the constructor publicly used
    public Department(University parent, String name) {
        super(parent);      // Call superclass (Child) constructor
        addToOwner();
        // Do other class specific things...
        this.name = name;
    }

    // This constructor is used internally by the transactional system. It is best practise to mark it so nobody deletes
    // it, despite appearing to be unused.
    @TransactionalConstructor
    protected Department(University owner) {
        super(owner);
    }

    @Override
    protected void removeFromOwner() {
        getOwner().departments.remove(this);
    }

    @Override
    protected void addToOwner() {
        getOwner().departments.add(this);
    }
}
