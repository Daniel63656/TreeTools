package net.scoreworks.collection;

import net.scoreworks.treetools.MutableObject;
import net.scoreworks.treetools.MappedChild;

import java.util.Map;
import java.util.TreeMap;

public class ContinuousRangeMap<O extends MutableObject, K, R extends MappedChild<O, K> & ContinuousRange<K>> extends TreeMap<K, R> {

    public R getRangeAt(K key) {
        Map.Entry<K, R> entry = floorEntry(key);
        if (entry != null)
            return entry.getValue();
        return null;
    }

}