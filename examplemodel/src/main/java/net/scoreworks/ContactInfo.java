package net.scoreworks;

import net.scoreworks.treetools.Child;
import net.scoreworks.treetools.annotations.PolymorphOwner;
import net.scoreworks.treetools.annotations.TransactionalConstructor;

@PolymorphOwner(commonInterface = Member.class)
public class ContactInfo extends Child<Member> {
    String emailAddress;
    int phoneNumber;

    public ContactInfo(Member owner, String emailAddress, int phoneNumber) {
        super(owner);
        this.emailAddress = emailAddress;
        this.phoneNumber = phoneNumber;
    }

    @TransactionalConstructor
    protected ContactInfo(Member owner) {
        super(owner);
    }

    @Override
    protected void removeFromOwner() {
        getOwner().info = null;
    }

    @Override
    protected void addToOwner() {
        getOwner().info = this;
    }
}
