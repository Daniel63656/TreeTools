package com.immersive.collection;

import java.util.ArrayList;
import java.util.HashMap;

public class HashStack<K, V> extends HashMap<K, ArrayList<V>> {

    public V getStackEntry(K key, int index) {
        if (!containsKey(key))
            return null;
        return get(key).get(index);
    }

    public V getStackTop(K key) {
        if (!containsKey(key))
            return null;
        ArrayList<V> stack = get(key);
        if (stack.isEmpty())
            return null;
        return stack.get(stack.size()-1);
    }

    public int pushValue(K key, V value) {
        if (!this.containsKey(key)) {
            ArrayList<V> stack = new ArrayList<>();
            this.put(key, stack);
            stack.add(value);
            return stack.size()-1;
        }
        ArrayList<V> stack = get(key);
        stack.add(value);
        return stack.size()-1;
    }

    public V popValue(K key) {
        if (!this.containsKey(key))
            return null;
        ArrayList<V> stack = get(key);
        V value = stack.get(stack.size()-1);
        stack.remove(stack.size()-1);
        return value;
    }

    public void removeValueFromStack(K key, V value) {
        if (!this.containsKey(key)) {
            return;
        }
        ArrayList<V> stack = get(key);
        stack.remove(value);
    }

    public int getStackSize(K key) {
        if (!containsKey(key))
            return 0;
        return get(key).size();
    }

    @Override
    public boolean isEmpty() {
        for (ArrayList<V> stack : values()) {
            if (!stack.isEmpty())
                return false;
        }
        return true;
    }

    public boolean isStackEmpty(K key) {
        if (!containsKey(key))
            return true;
        return get(key).isEmpty();
    }
}
