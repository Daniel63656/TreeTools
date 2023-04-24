package com.immersive.transactions;


import com.immersive.transactions.commits.CollapsedCommit;

class History {
    private final int capacity;     //TODO respect capacity
    CollapsedCommit ongoingCommit = new CollapsedCommit();
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
        //these commits are not inserted into transactionManagers commit list. They get copied, at which time they
        //get their proper id
        ongoingCommit = new CollapsedCommit();
    }

    public boolean undosAvailable() {
        return head != initialNode;
    }

    public boolean redosAvailable() {
        return head.next != null;
    }

    static class Node {
        CollapsedCommit self;
        Node previous, next;

        public Node(CollapsedCommit self) {
            this.self = self;
        }
    }
}
