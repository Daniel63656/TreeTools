package com.immersive.transactions;

import com.immersive.transactions.annotations.CrossReference;
import com.immersive.transactions.commits.Commit;
import com.immersive.transactions.exceptions.TransactionException;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.lang.reflect.Field;
import java.util.ArrayList;
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


    Remote(RootEntity rootEntity, CommitId commitId) {
        buildRemote(this, rootEntity, commitId);
    }
    private void buildRemote(Remote remote, MutableObject dme, CommitId commitId) {
        remote.createObjectState(dme, commitId);
        ArrayList<ChildEntity<?>> children = DataModelInfo.getChildren(dme);
        for (ChildEntity<?> child : children) {
            buildRemote(remote, child, commitId);
        }
    }

    /**
     * create an object state. Instantiating a state via the {@link Remote} makes sure, they are only created once per object and
     * nasty stuff like cross-references are properly handled
     * @param dme object to create the logical key for
     */
    public ObjectState createObjectState(MutableObject dme, CommitId currentId) {
        //avoid creating duplicate LOKs for same object within a tree. This also avoids infinite recursion when
        //two cross-references point at each other!
        if (containsValue(dme)) {
            return getKey(dme);
        }
        ObjectState objectState = new ObjectState(dme.getClass(), currentId);
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
                    ObjectState crossReference = createObjectState((MutableObject) fieldValue, currentId);
                    objectState.crossReferences.put(field, crossReference);
                }
            }
            //field is of primitive data type
            else {
                objectState.put(field, fieldValue);
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
    public static class ObjectState extends HashMap<Field, Object> {

        /**
         * corresponding class-type whose content is saved by this logical key
         */
        final Class<? extends MutableObject> clazz;

        private final CommitId creationId;

        /**
         * save cross-references in a separate map. The saved {@link ObjectState}s only point to valid entries in
         * the {@link Remote} in the commit that this key was created. 
         */
        public final HashMap<Field, ObjectState> crossReferences = new HashMap<>();

        /**
         * A unique {@link Remote} wide ID to identify the logical key. Necessary because all fields can be the same
         * for differnet objects in the tree. Needs to be int to be used as hash directly
         */
        private final int uniqueID;


        /**
         * constructor is private so that logical keys are only instantiated via the {@link Remote} that
         * they are held in
         */
        private ObjectState(Class<? extends MutableObject> clazz, CommitId creationId) {
            this.clazz = clazz;
            this.creationId = creationId;
            this.uniqueID = globalID;
            globalID++;
        }

        public CommitId getCreationId() {
            return creationId;
        }

        boolean logicallySameWith(ObjectState other) {
            for(Field f:keySet()) {
                if(!other.containsKey(f)) {
                    return false;
                }
                if(!Objects.equals(get(f), other.get(f))) {
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
            if (!isEmpty()) {
                printImmutableFields(strb);
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

        public String toString(CommitId currentCommitId) {
            StringBuilder strb = new StringBuilder();
            strb.append(clazz.getSimpleName()).append("[").append(uniqueID).append("]");
            if (!isEmpty()) {
                printImmutableFields(strb);
                for (Entry<Field, ObjectState> entry : crossReferences.entrySet()) {
                    ObjectState crossReferencedState = entry.getValue();
                    if (crossReferencedState == null)
                        strb.append(entry.getKey().getName()).append("=[null] ");
                    else {
                        strb.append(entry.getKey().getName()).append("=[").append(crossReferencedState.uniqueID);
                        ObjectState traced = crossReferencedState;
                        for (Commit c : TransactionManager.getInstance().commits.subMap(creationId, false, currentCommitId, true).values()) {
                            traced = c.traceForward(traced);
                        }
                        if (!traced.equals(crossReferencedState))
                            strb.append("->").append(traced.uniqueID);
                        strb.append("] ");
                    }
                }
                strb.setLength(strb.length() - 1);  //remove last space
                strb.append("}");
            }
            return strb.toString();
        }

        private void printImmutableFields(StringBuilder strb) {
            strb.append(" = {");
            for (Entry<Field, Object> entry : entrySet()) {
                if (entry.getValue() instanceof ObjectState)
                    strb.append(entry.getKey().getName()).append("=[").append(entry.getValue().hashCode()).append("] ");
                else
                    strb.append(entry.getKey().getName()).append("=").append(entry.getValue()).append(" ");
            }
        }
    }
}