package net.scoreworks;

public class Student extends Member {
    // This field does not exist as far as the transactional system is concerned
    transient Course course;

    public Student(University owner, Integer key, String name, ContactInfo info, Course course) {
        super(owner, key, name, info);    // Call Member constructor that adds Student to its Owner
        this.course = course;
    }

    @Override
    protected void addToOwner() {
        course.students.put(getKey(), this);
        super.addToOwner();
    }

    @Override
    protected void removeFromOwner() {
        course.students.remove(getKey());
        super.removeFromOwner();
    }
}
