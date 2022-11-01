package com.immersive.transactions;

import com.immersive.transactions.annotations.CrossReference;
import com.immersive.transactions.exceptions.TransactionException;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.lang.reflect.Field;
import java.util.Set;

public class LogicalObjectTree extends DualHashBidiMap<LogicalObjectKey, DataModelEntity> {

    LogicalObjectKey createLogicalObjectKey(DataModelEntity dme, Set<LogicalObjectKey> ctcr, boolean crossReferenceCall) {
        //avoid creating duplicate LOKs for same object inside a LOT!
        if (containsValue(dme)) {
            return getKey(dme);
        }
        LogicalObjectKey logicalObjectKey = new LogicalObjectKey(dme.getClass());
        //this avoids infinite recursion when two cross-references point on each other!
        put(logicalObjectKey, dme);
        if (crossReferenceCall && ctcr != null)
            ctcr.add(logicalObjectKey);
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
                else {
                    LogicalObjectKey crossReference = createLogicalObjectKey((DataModelEntity) fieldValue, ctcr, true);
                    logicalObjectKey.put(field, crossReference);
                    crossReference.subscribedLOKs.put(logicalObjectKey, field);
                }
            }
            //field is of primitive data type
            else {
                logicalObjectKey.put(field, fieldValue);
            }
        }
        return logicalObjectKey;
    }

    @Override
    public LogicalObjectKey removeValue(Object value) {
         LogicalObjectKey LOK = super.removeValue(value);
         LOK.unsubscribeFromCrossReferences();
         return LOK;
    }

    public LogicalObjectKey getLogicalObjectKeyOfOwner(ChildEntity<?> te) {
        if (!this.containsValue(te.getOwner())) {
            throw new TransactionException("Owner not found in LOT!", getKey(te).hashCode());
        }
        return this.getKey(te.getOwner());
    }
}