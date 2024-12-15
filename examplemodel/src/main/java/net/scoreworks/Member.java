package net.scoreworks;

import net.scoreworks.treetools.MappedChild;
import net.scoreworks.treetools.annotations.AbstractClass;
import net.scoreworks.treetools.annotations.TransactionalConstructor;

@AbstractClass(subclasses = {Student.class, Professor.class})   // For JSON parsing
public abstract class Member extends MappedChild<University, Integer> {
    String name;
    ContactInfo info;

    // This constructor will be called by class derivations
    protected Member(University owner, Integer key, String name, ContactInfo info) {
        super(owner, key);  // constructor of MappedChild
        this.name = name;
        this.info = info;
        addToOwner();       // add to owner since derivations call this constructor!
    }

    @TransactionalConstructor
    protected Member(University owner, Integer key) {
        super(owner, key);  // constructor of MappedChild
    }

    @Override
    protected void addToOwner() {
        getOwner().members.put(getKey(), this);
    }

    @Override
    protected void removeFromOwner() {
        getOwner().members.remove(getKey());
    }
}
