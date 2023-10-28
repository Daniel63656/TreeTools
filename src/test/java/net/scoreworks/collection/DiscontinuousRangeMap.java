package net.scoreworks.collection;

import net.scoreworks.treetools.MappedChild;
import net.scoreworks.treetools.MutableObject;

import java.util.Map;
import java.util.TreeMap;

public class DiscontinuousRangeMap<O extends MutableObject, K extends Comparable<K>, R extends MappedChild<O, K> & HasDuration<K>> extends TreeMap<K, R> {

    public R getRangeAt(K key) {
        Map.Entry<K, R> entry = floorEntry(key);
        if (entry != null) {
            if (entry.getValue().getEndKey().compareTo(key) < 0)
                return null;
            return entry.getValue();
        }
        return null;
    }

}