package com.immersive.collection;

import com.immersive.core.DataModelEntity;
import com.immersive.core.KeyedChildEntity;

import java.util.Map;
import java.util.TreeMap;

public class ContinuousRangeMap<O extends DataModelEntity, K, R extends KeyedChildEntity<O, K> & ContinuousRange<K>> extends TreeMap<K, R> {

    public R getRangeAt(K key) {
        Map.Entry<K, R> entry = floorEntry(key);
        if (entry != null)
            return entry.getValue();
        return null;
    }

}