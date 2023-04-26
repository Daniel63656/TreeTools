package com.immersive.transactions;

import com.immersive.transactions.exceptions.TransactionException;
import org.apache.commons.collections4.MapUtils;
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

    Remote(RootEntity rootEntity) {
        buildRemote(this, rootEntity);
    }
    private void buildRemote(Remote remote, MutableObject dme) {
        remote.createObjectState(dme);
        ArrayList<ChildEntity<?>> children = DataModelInfo.getChildren(dme);
        for (ChildEntity<?> child : children) {
            buildRemote(remote, child);
        }
    }

    /**
     * create an {@link ObjectState}. Instantiating a state via the {@link Remote} makes sure, they are only created once per object and
     * nasty stuff like cross-references are properly handled
     * @param dme object to create the logical key for
     */
    public ObjectState createObjectState(MutableObject dme) {
        //avoid creating duplicate states for same object within a remote. This also avoids infinite recursion when
        //two cross-references point at each other!
        if (containsValue(dme)) {
            return getKey(dme);
        }
        ObjectState objectState = new ObjectState(dme.getClass(), TransactionManager.objectID);
        TransactionManager.objectID++;
        put(objectState, dme);
        assignFieldsToObjectState(objectState, dme);
        return objectState;
    }

    public ObjectState updateObjectState(MutableObject dme, ObjectState oldState) {
        ObjectState objectState = new ObjectState(dme.getClass(), oldState.objectId);
        //put overrides existing values but not existing keys which we also want -> remove old entry first
        remove(oldState);
        put(objectState, dme);
        assignFieldsToObjectState(objectState, dme);
        return objectState;
    }

    private void assignFieldsToObjectState(ObjectState objectState, MutableObject dme) {
        for (Field field : DataModelInfo.getContentFields(dme)) {
            Object fieldValue = null;
            try {
                field.setAccessible(true);
                fieldValue = field.get(dme);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            if (fieldValue instanceof MutableObject) {
                objectState.fields.put(field, createObjectState((MutableObject) fieldValue));
            }
            else objectState.fields.put(field, fieldValue);
        }
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
    public static class ObjectState {

        /**
         * corresponding class-type whose content is saved by this state
         */
        final Class<? extends MutableObject> clazz;

        /**
         * map fields to their corresponding values. If the field holds another {@link MutableObject}, then the corresponding
         * {@link ObjectState} is used
         */
        private final HashMap<Field, Object> fields = new HashMap<>();

        /**
         * can't use the values of the fields as hash because they can be the same for several objects of the data model.
         * Use a {@link Remote}-wide unique id instead
         */
        private final long objectId;

        /**
         * constructor is private so that states are only instantiated via the {@link Remote} that
         * they are held in
         */
        private ObjectState(Class<? extends MutableObject> clazz, long objectId) {
            this.clazz = clazz;
            this.objectId = objectId;
        }

        public Map<Field, Object> getFields() {
            return MapUtils.unmodifiableMap(fields);
        }

        boolean contentEquals(ObjectState other) {
            for(Field f:fields.keySet()) {
                if(!other.fields.containsKey(f)) {
                    return false;
                }
                if(!Objects.equals(fields.get(f), other.fields.get(f))) {
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
            return this.objectId == other.objectId;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(objectId);
        }

        @Override
        public String toString() {
            StringBuilder strb = new StringBuilder();
            strb.append(clazz.getSimpleName()).append("[").append(objectId).append("] = {");
            if (!fields.isEmpty()) {
                for (Entry<Field, Object> entry : fields.entrySet()) {
                    if (entry.getValue() == null)
                        strb.append(entry.getKey().getName()).append("=[null] ");
                    else if (entry.getValue() instanceof ObjectState)
                        strb.append(entry.getKey().getName()).append("=[").append(entry.getValue().hashCode()).append("] ");
                    else
                        strb.append(entry.getKey().getName()).append("=").append(entry.getValue()).append(" ");
                }
            }
            //remove last space if attributes exist
            if (strb.charAt(strb.length()-1) == ' ')
                strb.setLength(strb.length() - 1);
            strb.append("}");
            return strb.toString();
        }
    }
}