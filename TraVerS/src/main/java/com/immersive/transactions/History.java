package com.immersive.transactions;

import java.util.Map;

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
        for (Map.Entry<LogicalObjectKey, Object[]> entry : commit.deletionRecords.entrySet()) {
            if (ongoingCommit.changeRecords.containsValue(entry.getKey())) {
                ongoingCommit.deletionRecords.put(ongoingCommit.changeRecords.getKey(entry.getKey()), entry.getValue());
                ongoingCommit.changeRecords.removeValue(entry.getKey());
            }
            else {
                if (ongoingCommit.creationRecords.containsKey(entry.getKey())) {
                    ongoingCommit.creationRecords.remove(entry.getKey());
                }
                else {
                    ongoingCommit.deletionRecords.put(entry.getKey(), entry.getValue());
                }
            }
        }
        for (Map.Entry<LogicalObjectKey, LogicalObjectKey> entry : commit.changeRecords.entrySet()) {
            if (ongoingCommit.changeRecords.containsValue(entry.getKey()))
                ongoingCommit.changeRecords.put(ongoingCommit.changeRecords.getKey(entry.getKey()), entry.getValue());
            else if (ongoingCommit.creationRecords.containsKey(entry.getKey())) {
                ongoingCommit.creationRecords.put(entry.getValue(), ongoingCommit.creationRecords.get(entry.getKey()));
                ongoingCommit.creationRecords.remove(entry.getKey());
            }
            else
                ongoingCommit.changeRecords.put(entry.getKey(), entry.getValue());
        }
        //handle key was referenced in params of creation records
        for (Object[] objects : ongoingCommit.creationRecords.values()) {
            for (int i=0; i<objects.length; i++) {
                if (objects[i] instanceof LogicalObjectKey) {
                    LogicalObjectKey LOK = (LogicalObjectKey) objects[i];
                    if (commit.changeRecords.containsKey((LogicalObjectKey) objects[i]))
                        objects[i] = commit.changeRecords.get(LOK);
                }
            }
        }
        //handle key was referenced in params of deletion records
        for (Object[] objects : ongoingCommit.deletionRecords.values()) {
            for (int i=0; i<objects.length; i++) {
                if (objects[i] instanceof LogicalObjectKey) {
                    LogicalObjectKey LOK = (LogicalObjectKey) objects[i];
                    if (commit.changeRecords.containsKey((LogicalObjectKey) objects[i]))
                        objects[i] = commit.changeRecords.get(LOK);
                }
            }
        }
        //unnecessary to also loop over those to check for old references in params
        ongoingCommit.creationRecords.putAll(commit.creationRecords);
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
