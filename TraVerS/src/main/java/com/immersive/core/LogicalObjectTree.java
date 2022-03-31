package com.immersive.core;

import com.immersive.annotations.CrossReference;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.lang.reflect.Field;

class LogicalObjectTree extends DualHashBidiMap<LogicalObjectKey, DataModelEntity> {

    LogicalObjectKey createLogicalObjectKey(DataModelEntity dme) {
        //avoid creating duplicate LOKs for same object inside a LOT!
        if (containsValue(dme)) {
            return getKey(dme);
        }
        LogicalObjectKey logicalObjectKey = new LogicalObjectKey(dme.getClass());
        //this avoids infinite recursion when two cross-references point on each other!
        put(logicalObjectKey, dme);
        //start iterating over fields using reflections
        for (Field field : TransactionManager.getContentFields(dme)) {
            Object fieldValue = null;
            try {
                field.setAccessible(true);
                fieldValue = field.get(dme);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            //field is a cross-reference
            if (field.getAnnotation(CrossReference.class) != null) {
                if (fieldValue == null)
                    logicalObjectKey.put(field, null);
                else
                    logicalObjectKey.put(field, createLogicalObjectKey((DataModelEntity) fieldValue));
            }
            //field is of primitive data type
            else {
                logicalObjectKey.put(field, fieldValue);
            }
        }
        return logicalObjectKey;
    }

    LogicalObjectKey getLogicalObjectKeyOfOwner(ChildEntity<?> te) {
        if (!this.containsValue(te.getOwner())) {
            throw new RuntimeException("Owner not found in LOT!");
        }
        return this.getKey(te.getOwner());
    }
}