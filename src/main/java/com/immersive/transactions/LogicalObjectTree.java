package com.immersive.transactions;

import com.immersive.transactions.annotations.CrossReference;
import com.immersive.transactions.exceptions.TransactionException;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;


/**
 * A data structure providing a two-way-link between {@link DataModelEntity} and corresponding
 * logical content of the object (represented by a {@link LogicalObjectKey}).
 */
public class LogicalObjectTree extends DualHashBidiMap<LogicalObjectTree.LogicalObjectKey, DataModelEntity> {

    /**
     * make sure each {@link LogicalObjectKey} gets assigned a unique ID
     */
    private static int globalID;

    /**
     * create a logical key. Instantiating a key via the tree makes sure, they are only created once per object and
     * handles nasty stuff like cross-references
     * @param dme object to create the logical key for
     */
    LogicalObjectKey createLogicalObjectKey(DataModelEntity dme) {
        //avoid creating duplicate LOKs for same object within a tree. This also avoids infinite recursion when
        //two cross-references point at each other!
        if (containsValue(dme)) {
            return getKey(dme);
        }
        LogicalObjectKey logicalObjectKey = new LogicalObjectKey(dme.getClass());
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
                else {
                    LogicalObjectKey crossReference = createLogicalObjectKey((DataModelEntity) fieldValue);
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

    /**
     * Class containing the logical content of an object as field-object pairs. Logical content is considered to be all
     * {@link com.immersive.transactions.DataModelInfo#contentFields} and therefore excludes the owner, keys the class
     * is mapped by the owner and children. These objects can be instead determined via the links provided by the
     * {@link com.immersive.transactions.LogicalObjectTree}.
     */
    static class LogicalObjectKey extends HashMap<Field, Object> {

        /**
         * corresponding class-type whose content is saved by this logical key
         */
        Class<? extends DataModelEntity> clazz;

        /**
         * save other logical keys that depend on this one (due to cross-references) together with the responsible field
         */
        HashMap<LogicalObjectKey, Field> subscribedLOKs = new HashMap<>();

        /**
         * A unique {@link LogicalObjectTree} wide ID to identify the logical key. Necessary because all fields can be the same
         * for differnet objects in the tree
         */
        private final int uniqueID;


        /**
         * constructor is private so that logical keys are only instantiated via the {@link LogicalObjectTree} that
         * they are held in
         */
        private LogicalObjectKey(Class<? extends DataModelEntity> clazz) {
            this.clazz = clazz;
            this.uniqueID = globalID;
            globalID++;
        }

        private void unsubscribeFromCrossReferences() {
            for (Object obj : this.values()) {
                if (obj instanceof LogicalObjectKey) {
                    ((LogicalObjectKey) obj).subscribedLOKs.remove(this);
                }
            }
        }

        boolean logicallySameWith(LogicalObjectKey lok) {
            for(Field f:keySet()) {
                if(!lok.containsKey(f)) {
                    return false;
                }
                if(!Objects.equals(this.get(f), lok.get(f))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof LogicalObjectKey)) {
                return false;
            }
            LogicalObjectKey right = (LogicalObjectKey) o;
            return this.uniqueID == right.uniqueID;
        }

        @Override
        public int hashCode() {
            return uniqueID;
        }

        String printSubscribers() {
            StringBuilder strb = new StringBuilder();
            if (!subscribedLOKs.isEmpty()) {
                strb.append(" subscribed: ");
                for (LogicalObjectKey LOK : subscribedLOKs.keySet()) {
                    strb.append("["). append(LOK.uniqueID).append("]");
                }
            }
            return strb.toString();
        }

        @Override
        public String toString() {
            StringBuilder strb = new StringBuilder();
            strb.append(clazz.getSimpleName()).append("(").append(uniqueID).append(")");
            if (!isEmpty()) {
                strb.append("={");
                for (Entry<Field, Object> entry : entrySet()) {
                    if (entry.getValue() instanceof LogicalObjectKey)
                        strb.append(entry.getKey().getName()).append("=[").append(entry.getValue().hashCode()).append("]");
                    else
                        strb.append(entry.getKey().getName()).append("=").append(entry.getValue());
                    strb.append(" ");
                }
                strb.setLength(strb.length() - 1);  //remove last space
                strb.append("}");
            }
            return strb.toString();
        }
    }
}