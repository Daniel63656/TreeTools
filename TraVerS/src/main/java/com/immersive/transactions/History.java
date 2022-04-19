package com.immersive.transactions;

class History {
    private final int capacity;
    Commit ongoingCommit = new Commit(null);
    Node head;

    History(int capacity) {
        this.capacity = capacity;
    }

    public void addToOngoingCommit(Commit commit) {
        commit.addTo(ongoingCommit);
    }

    void archive() {
        if (ongoingCommit.isEmpty())
            return;
        Node node = new Node(ongoingCommit);
        if (head != null) {
            head.next = node;
            node.previous = head;
        }
        head = node;
        ongoingCommit = new Commit(null);
    }

    public boolean undosAvailable() {
        return head != null;
    }

    public boolean redosAvailable() {
        if (head == null)
            return false;
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
