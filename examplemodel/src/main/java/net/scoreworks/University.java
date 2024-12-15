package net.scoreworks;

import net.scoreworks.treetools.RootEntity;
import net.scoreworks.treetools.annotations.TransactionalConstructor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class University extends RootEntity {
    Set<Department> departments = new HashSet<>();
    Map<Integer, Member> members = new HashMap<>();

    // Not necessary, but nice to mark this constructor anyway
    @TransactionalConstructor
    public University() {}
}
