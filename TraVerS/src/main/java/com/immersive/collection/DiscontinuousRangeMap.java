package com.immersive.collection;

import com.immersive.core.DataModelEntity;
import com.immersive.core.DoubleKeyedChildEntity;

import java.util.Map;
import java.util.TreeMap;

public class DiscontinuousRangeMap<O extends DataModelEntity, K extends Comparable<K>, R extends DoubleKeyedChildEntity<O, K> & HasDuration> extends TreeMap<K, R> {

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