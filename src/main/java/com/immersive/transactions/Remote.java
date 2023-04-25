package com.immersive.transactions;

import com.immersive.transactions.annotations.CrossReference;
import com.immersive.transactions.commits.Commit;
import com.immersive.transactions.exceptions.TransactionException;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


/**
 * A data structure providing a two-way-link between a {@link MutableObject} and its corresponding
 * {@link ObjectState} (at a particular {@link CommitId}). Because {@link ObjectState}s are immutable, this
 * acts as a remote state the data model can revert to while uncommitted changes exist.
 */
public class Remote extends DualHashBidiMap<Remote.ObjectState, MutableObject> {

    /**
     * make sure each created {@link ObjectState} gets assigned a unique ID within the {@link Remote}
     */
    private static int remoteWideId;


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
     * create an {@link ObjectState}. Instantiating a state via the {@link Remote} makes sure, they are only created once per object and
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
        if (getKey(te) == null) {
            throw new TransactionException("remote didn't contain owner of object", getKey(te).hashCode());
        }
        if (!this.containsValue(te.getOwner())) {
            throw new TransactionException("remote didn't contain owner of object", getKey(te).hashCode());
        }
        return this.getKey(te.getOwner());
    }

    /**
     * Class that acts as a key for a given objects state at a given {@link CommitId}. Primarily saves the immutable
     * {@link com.immersive.transactions.DataModelInfo#contentFields} of an object (that excludes the owner, keys
     * and children). This state is linked up with the corresponding object within the {@link Remote}.
     * This object must be immutable after its full construction within a commit.
     */
    public static class ObjectState extends HashMap<Field, Object> {

        /**
         * corresponding class-type whose content is saved by this state
         */
        final Class<? extends MutableObject> clazz;

        /**
         * can't use the values of the fields as hash because they can be the same for several objects of the data model.
         * Use a {@link Remote}-wide unique id instead
         */
        private final int hashCode;

        /**
         * the {@link CommitId} at which this state was created. Together with the {@link ObjectState#hashCode} this object
         * becomes uniquely identifiable across different {@link Remote}s and {@link Commit}s
         */
        private final CommitId creationId;

        /**
         * save cross-references in a separate map. The saved {@link ObjectState}s only point to valid entries in
         * the {@link Remote} in the commit that this state was created. This map gets modified in the commit creation but
         * is supposed to be immutable afterwards
         */
        final HashMap<Field, ObjectState> crossReferences = new HashMap<>();

        /**
         * constructor is private so that states are only instantiated via the {@link Remote} that
         * they are held in
         */
        private ObjectState(Class<? extends MutableObject> clazz, CommitId creationId) {
            this.clazz = clazz;
            this.creationId = creationId;
            this.hashCode = remoteWideId;
            remoteWideId++;
        }

        public CommitId getCreationId() {
            return creationId;
        }

        public Map<Field, ObjectState> getCrossReferences() {
            return crossReferences;
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
            ObjectState other = (ObjectState) o;
            //different states from different remotes (or even the same remote if hashCode overflows) may have the
            //same hasCode but not the same creationId as well
            return this.hashCode == other.hashCode && this.creationId == other.creationId;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            StringBuilder strb = new StringBuilder();
            strb.append(clazz.getSimpleName()).append("[").append(hashCode).append("]");
            if (!isEmpty()) {
                printImmutableFields(strb);
                for (Entry<Field, ObjectState> entry : crossReferences.entrySet()) {
                    if (entry.getValue() == null)
                        strb.append(entry.getKey().getName()).append("=[null] ");
                    else
                        strb.append(entry.getKey().getName()).append("=[").append(entry.getValue().hashCode).append("] ");
                }
                strb.setLength(strb.length() - 1);  //remove last space
                strb.append("}");
            }
            return strb.toString();
        }

        public String toString(CommitId currentCommitId) {
            StringBuilder strb = new StringBuilder();
            strb.append(clazz.getSimpleName()).append("[").append(hashCode).append("]");
            if (!isEmpty()) {
                printImmutableFields(strb);
                for (Entry<Field, ObjectState> entry : crossReferences.entrySet()) {
                    ObjectState crossReferencedState = entry.getValue();
                    if (crossReferencedState == null)
                        strb.append(entry.getKey().getName()).append("=[null] ");
                    else {
                        strb.append(entry.getKey().getName()).append("=[").append(crossReferencedState.hashCode);
                        ObjectState traced = crossReferencedState;
                        for (Commit c : TransactionManager.getInstance().commits.subMap(creationId, false, currentCommitId, true).values()) {
                            traced = c.traceForward(traced);
                        }
                        if (!traced.equals(crossReferencedState))
                            strb.append("->").append(traced.hashCode);
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