package com.immersive.transactions;

import com.immersive.transactions.annotations.CrossReference;
import com.immersive.transactions.exceptions.TransactionException;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Objects;


/**
 * A data structure providing a two-way-link between {@link MutableObject} and corresponding
 * logical content of the object (represented by a {@link ObjectState}). These keys are immutable, therefore this
 * acts as a remote state the data model can revert to while uncommitted changes exist.
 */
public class Remote extends DualHashBidiMap<Remote.ObjectState, MutableObject> {

    /**
     * make sure each {@link ObjectState} gets assigned a unique ID
     */
    private static int globalID;

    /**
     * create an object state. Instantiating a state via the {@link Remote} makes sure, they are only created once per object and
     * nasty stuff like cross-references are properly handled
     * @param dme object to create the logical key for
     */
    ObjectState createObjectState(MutableObject dme) {
        //avoid creating duplicate LOKs for same object within a tree. This also avoids infinite recursion when
        //two cross-references point at each other!
        if (containsValue(dme)) {
            return getKey(dme);
        }
        ObjectState objectState = new ObjectState(dme.getClass());
        put(objectState, dme);

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
                    objectState.crossReferences.put(field, null);
                else {
                    ObjectState crossReference = createObjectState((MutableObject) fieldValue);
                    objectState.crossReferences.put(field, crossReference);
                }
            }
            //field is of primitive data type
            else {
                objectState.content.put(field, fieldValue);
            }
        }
        return objectState;
    }

    public ObjectState getLogicalObjectKeyOfOwner(ChildEntity<?> te) {
        if (!this.containsValue(te.getOwner())) {
            throw new TransactionException("remote didn't contain owner of object", getKey(te).hashCode());
        }
        return this.getKey(te.getOwner());
    }

    /**
     * Class containing the logical content of an object as field-object pairs. Logical content is considered to be all
     * {@link com.immersive.transactions.DataModelInfo#contentFields} and therefore excludes the owner, keys
     * and children. Is designed as a IMMUTABLE class, meaning that all saved values stay constant. If a value requires
     * a change, this is expressed by creating a new key entirely.
     */
    static class ObjectState {

        /**
         * corresponding class-type whose content is saved by this logical key
         */
        final Class<? extends MutableObject> clazz;

        /**
         * save logical content of an object, mapped by field
         */
        final HashMap<Field, Object> content = new HashMap<>();

        /**
         * save cross-references in a separate map. The saved {@link ObjectState}s only point to valid entries in
         * the {@link Remote} in the commit that this key was created. These cross-referenced objects may
         * get new keys in later commits, which is fine because the cross-reference itself is unmodified
         */
        final HashMap<Field, ObjectState> crossReferences = new HashMap<>();

        /**
         * A unique {@link Remote} wide ID to identify the logical key. Necessary because all fields can be the same
         * for differnet objects in the tree
         */
        private final int uniqueID;


        /**
         * constructor is private so that logical keys are only instantiated via the {@link Remote} that
         * they are held in
         */
        private ObjectState(Class<? extends MutableObject> clazz) {
            this.clazz = clazz;
            this.uniqueID = globalID;
            globalID++;
        }

        boolean logicallySameWith(ObjectState other) {
            for(Field f:content.keySet()) {
                if(!other.content.containsKey(f)) {
                    return false;
                }
                if(!Objects.equals(content.get(f), other.content.get(f))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ObjectState)) {
                return false;
            }
            ObjectState right = (ObjectState) o;
            return this.uniqueID == right.uniqueID;
        }

        @Override
        public int hashCode() {
            return uniqueID;
        }

        @Override
        public String toString() {
            StringBuilder strb = new StringBuilder();
            strb.append(clazz.getSimpleName()).append("[").append(uniqueID).append("]");
            if (!content.isEmpty()) {
                strb.append(" = {");
                for (Entry<Field, Object> entry : content.entrySet()) {
                    if (entry.getValue() instanceof ObjectState)
                        strb.append(entry.getKey().getName()).append("=[").append(entry.getValue().hashCode()).append("] ");
                    else
                        strb.append(entry.getKey().getName()).append("=").append(entry.getValue()).append(" ");
                }
                for (Entry<Field, ObjectState> entry : crossReferences.entrySet()) {
                    if (entry.getValue() == null)
                        strb.append(entry.getKey().getName()).append("=[null] ");
                    else
                        strb.append(entry.getKey().getName()).append("=[").append(entry.getValue().uniqueID).append("] ");
                }
                strb.setLength(strb.length() - 1);  //remove last space
                strb.append("}");
            }
            return strb.toString();
        }
    }
}