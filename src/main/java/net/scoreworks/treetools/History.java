/*
 * Copyright (c) 2023 Daniel Maier.
 * Licensed under the MIT License.
 */

package net.scoreworks.treetools;

import net.scoreworks.treetools.commits.Commit;

class History {
    Commit ongoingCommit = new Commit();
    Node initialNode;  //node without commit to mark the start
    Node head;

    History() {
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
        ongoingCommit = new Commit();
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
