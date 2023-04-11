package com.immersive.collection;

import com.immersive.transactions.DataModelEntity;
import com.immersive.transactions.DoubleKeyedChildEntity;

import java.util.Map;
import java.util.TreeMap;

public class DiscontinuousRangeMap<O extends DataModelEntity, K extends Comparable<K>, R extends DoubleKeyedChildEntity<O, K> & HasDuration<K>> extends TreeMap<K, R> {

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