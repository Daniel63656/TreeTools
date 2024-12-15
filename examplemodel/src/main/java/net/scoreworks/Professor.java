package net.scoreworks;

public class Professor extends Member {
    Department department;

    public Professor(University owner, Integer key, String name, ContactInfo info, Department department) {
        super(owner, key, name, info);    // Call Member constructor that adds Student to its Owner
        this.department = department;
    }
}
