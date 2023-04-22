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
 * logical content of the object (represented by a {@link LogicalObjectKey}). Acts as a remote state the data model
 * can revert to while uncommitted changes exist.
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
        for (Field field : DataModelInfo.getContentFields(dme)) {
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
                    logicalObjectKey.crossReferences.put(field, null);
                else {
                    LogicalObjectKey crossReference = createLogicalObjectKey((DataModelEntity) fieldValue);
                    logicalObjectKey.crossReferences.put(field, crossReference);
                }
            }
            //field is of primitive data type
            else {
                logicalObjectKey.put(field, fieldValue);
            }
        }
        return logicalObjectKey;
    }

    public LogicalObjectKey getLogicalObjectKeyOfOwner(ChildEntity<?> te) {
        if (!this.containsValue(te.getOwner())) {
            throw new TransactionException("Owner not found in LOT!", getKey(te).hashCode());
        }
        return this.getKey(te.getOwner());
    }

    /**
     * Class containing the logical content of an object as field-object pairs. Logical content is considered to be all
     * {@link com.immersive.transactions.DataModelInfo#contentFields} and therefore excludes the owner, keys
     * and children. Is designed as a IMMUTABLE class, meaning that all saved values stay constant. If a value requires
     * a change, this is expressed by creating a new key entirely.
     */
    static class LogicalObjectKey extends HashMap<Field, Object> {

        /**
         * corresponding class-type whose content is saved by this logical key
         */
        final Class<? extends DataModelEntity> clazz;

        /**
         * save cross-references in a separate map. The saved {@link LogicalObjectKey}s only point to valid entries in
         * the {@link LogicalObjectTree} in the commit that this key was created. These cross-referenced objects may
         * get new keys in later commits, which is fine because the cross-reference itself is unmodified
         */
        final HashMap<Field, LogicalObjectKey> crossReferences = new HashMap<>();

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

        @Override
        public String toString() {
            StringBuilder strb = new StringBuilder();
            strb.append(clazz.getSimpleName()).append("(").append(uniqueID).append(")");
            if (!isEmpty()) {
                strb.append(" = {");
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