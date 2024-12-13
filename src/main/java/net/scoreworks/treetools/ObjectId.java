/*
 * Copyright (c) 2023 Daniel Maier.
 * Licensed under the MIT License.
 */

package net.scoreworks.treetools;

import org.jetbrains.annotations.NotNull;


/**
 * Acts as a unique id for every created object across all {@link Repository}s. Each instantiation increments the id
 */
public class ObjectId implements Comparable<ObjectId> {
    private static int currentObjectId;
    static void reset() {
        currentObjectId = 0;
    }
    private final long id;

    public ObjectId() {
        this.id = currentObjectId;
        currentObjectId++;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ObjectId)) {
            return false;
        }
        ObjectId other = (ObjectId) o;
        return this.id == other.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public int compareTo(@NotNull ObjectId right) {
        return Long.compare(id, right.id);
    }

    @Override
    public String toString() {
        return Long.toString(id);
    }
}
