package com.immersive.transactions;

import java.util.Map;
import com.immersive.transactions.Remote.ObjectState;

class History {
    private final int capacity;     //TODO respect capacity
    Commit ongoingCommit = new Commit(null);
    Node initialNode;  //node without commit to mark the start
    Node head;

    History(int capacity) {
        this.capacity = capacity;
        initialNode = new Node(null);
        head = initialNode;
    }

    void createUndoState() {
        if (ongoingCommit.isEmpty())
            return;
        Node node = new Node(ongoingCommit);
        head.next = node;
        node.previous = head;
        head = node;
        ongoingCommit = new Commit(null);
    }

    public void addToOngoingCommit(Commit commit) {
        for (Map.Entry<ObjectState, Object[]> entry : commit.creationRecords.entrySet()) {
            ObjectState creationState = entry.getKey();
            if (ongoingCommit.creationRecords.containsKey(creationState) ||
                ongoingCommit.deletionRecords.containsKey(creationState) ||
                ongoingCommit.changeRecords.containsValue(creationState))
                throw new RuntimeException("Tried to create an object already present in commit!");
            ongoingCommit.creationRecords.put(creationState, entry.getValue());
        }

        for (Map.Entry<ObjectState, Object[]> entry : commit.deletionRecords.entrySet()) {
            ObjectState deleteState = entry.getKey();
            //deletion is contained as after key in a change
            if (ongoingCommit.changeRecords.containsValue(deleteState)) {
                ObjectState beforeState = ongoingCommit.changeRecords.getKey(deleteState);
                ongoingCommit.deletionRecords.put(beforeState, entry.getValue());
                ongoingCommit.changeRecords.removeValue(deleteState);
            }
            //deletion is in creationRecords
            else if (ongoingCommit.creationRecords.containsKey(deleteState)) {
                ongoingCommit.creationRecords.remove(deleteState);
            }
            else if (ongoingCommit.deletionRecords.containsKey(deleteState))
                throw new RuntimeException("Tried to delete an object that is already deleted!");
            //not contained so far
            else {
                ongoingCommit.deletionRecords.put(deleteState, entry.getValue());
            }
        }
        
        for (Map.Entry<ObjectState, ObjectState> entry : commit.changeRecords.entrySet()) {
            ObjectState before = entry.getKey();
            ObjectState after = entry.getValue();
            //change was considered a creation so far - update creation record
            if (ongoingCommit.creationRecords.containsKey(before)) {
                ongoingCommit.creationRecords.put(after, ongoingCommit.creationRecords.get(before));
                ongoingCommit.creationRecords.remove(before);
            }
            else if (ongoingCommit.deletionRecords.containsKey(before))
                throw new RuntimeException("Tried to change an object that is already deleted!");
            //change is contained as after key in existing change - update change record
            else if (ongoingCommit.changeRecords.containsValue(before))
                ongoingCommit.changeRecords.put(ongoingCommit.changeRecords.getKey(before), after);
            //not contained so far
            else
                ongoingCommit.changeRecords.put(before, entry.getValue());
        }
        
        
        //handle key was referenced in params of creation records
        for (Object[] objects : ongoingCommit.creationRecords.values()) {
            for (int i=0; i<objects.length; i++) {
                if (objects[i] instanceof ObjectState) {
                    ObjectState LOK = (ObjectState) objects[i];
                    if (commit.changeRecords.containsKey((ObjectState) objects[i]))
                        objects[i] = commit.changeRecords.get(LOK);
                }
            }
        }
        //handle key was referenced in params of deletion records
        for (Object[] objects : ongoingCommit.deletionRecords.values()) {
            for (int i=0; i<objects.length; i++) {
                if (objects[i] instanceof ObjectState) {
                    ObjectState LOK = (ObjectState) objects[i];
                    if (commit.changeRecords.containsKey((ObjectState) objects[i]))
                        objects[i] = commit.changeRecords.get(LOK);
                }
            }
        }
    }

    public boolean undosAvailable() {
        return head != initialNode;
    }

    public boolean redosAvailable() {
        return head.next != null;
    }

    static class Node {
        Commit self;
        Node previous, next;

        public Node(Commit self) {
            this.self = self;
        }
    }
}
