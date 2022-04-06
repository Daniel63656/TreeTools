package com.immersive.collection;

import java.util.ArrayList;
import java.util.HashMap;

public class HashStack<K, V extends Comparable<V>> extends HashMap<K, ArrayList<V>> {

    public V getNthStackEntry(K key, int index) {
        return super.get(key).get(index);
    }

    public int putValue(K key, V value) {
        if (!this.containsKey(key)) {
            ArrayList<V> stack = new ArrayList<>();
            this.put(key, stack);
            stack.add(value);
            return stack.size()-1;
        }
        ArrayList<V> stack = get(key);
        stack.add(value);
        return 0;
    }

    public void removeValue(K key, V value) {
        if (!this.containsKey(key)) {
            return;
        }
        ArrayList<V> stack = get(key);
        stack.remove(value);
    }
}
