/*
 * Copyright (c) 2023 Daniel Maier.
 * Licensed under the MIT License.
 */

package net.scoreworks.treetools;

import net.scoreworks.treetools.exceptions.TransactionException;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


/**
 * A data structure providing a two-way link between {@link MutableObject}s and corresponding
 * {@link ObjectState}s (at a particular {@link CommitId}). Because {@link ObjectState}s are immutable, this
 * acts as a remote state the data model can revert to while uncommitted changes exist.
 */
public class Remote extends DualHashBidiMap<Remote.ObjectState, MutableObject> {

    Remote(RootEntity rootEntity) {
        buildRemote(this, rootEntity);
    }
    private void buildRemote(Remote remote, MutableObject mo) {
        remote.createObjectState(mo);
        ArrayList<Child<?>> children = ClassMetadata.getChildren(mo);
        for (Child<?> child : children) {
            buildRemote(remote, child);
        }
    }

    /**
     * Create an {@link ObjectState}. Instantiating a state via the {@link Remote} makes sure, they are only created once per object and
     * nasty stuff like cross-references are properly handled
     * @param mo object to create the logical key for
     */
    public ObjectState createObjectState(MutableObject mo) {
        //avoid creating duplicate states for same object within a remote. This also avoids infinite recursion when
        //two cross-references point at each other!
        if (containsValue(mo)) {
            return getKey(mo);
        }
        ObjectState objectState = new ObjectState(mo.getClass(), mo.constructorParameterObjects(), new ObjectId());
        put(objectState, mo);
        assignFieldsToObjectState(objectState, mo);
        return objectState;
    }

    public ObjectState updateObjectState(MutableObject mo, ObjectState oldState) {
        ObjectState objectState = new ObjectState(mo.getClass(), mo.constructorParameterObjects(), oldState.objectId);
        //put overrides existing values but not existing keys which we also want -> remove old entry first
        remove(oldState);
        put(objectState, mo);
        assignFieldsToObjectState(objectState, mo);
        return objectState;
    }

    private void assignFieldsToObjectState(ObjectState objectState, MutableObject mo) {
        for (Field field : ClassMetadata.getFields(mo)) {
            Object fieldValue = null;
            try {
                field.setAccessible(true);
                fieldValue = field.get(mo);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            if (fieldValue instanceof MutableObject) {
                objectState.fields.put(field, createObjectState((MutableObject) fieldValue));
            }
            else objectState.fields.put(field, fieldValue);
        }
    }

    public ObjectState getLogicalObjectKeyOfOwner(Child<?> ch) {
        if (getKey(ch) == null) {
            throw new TransactionException("remote didn't contain owner of object", getKey(ch).hashCode());
        }
        if (!this.containsValue(ch.getOwner())) {
            throw new TransactionException("remote didn't contain owner of object", getKey(ch).hashCode());
        }
        return this.getKey(ch.getOwner());
    }

    /**
     * Class that acts as a key for a given object's state at a given {@link CommitId}. Primarily saves the immutable
     * {@link ClassMetadata#fields} of an object (that excludes the owner, keys
     * and children). This state is linked up with the corresponding object within the {@link Remote}.
     * This object must be immutable after its full construction within a commit.
     */
    public class ObjectState {

        /**
         * Corresponding class-type whose content is saved by this state
         */
        final Class<? extends MutableObject> clazz;

        /**
         * Save constructor parameters as they might also be subject to change (migration). If the param holds another
         * {@link MutableObject}, then the corresponding {@link ObjectState} is used
         */
        private final Object[] constructionParams;

        /**
         * Map fields to their corresponding values. If the field holds another {@link MutableObject}, then the corresponding
         * {@link ObjectState} is used
         */
        private final HashMap<Field, Object> fields = new HashMap<>();

        /**
         * Can't use the values of the fields as hash because they can be the same for several objects of the data model.
         * Use a {@link Remote}-wide unique id instead
         */
        private final ObjectId objectId;

        /**
         * Constructor is private so that states are only instantiated via the {@link Remote} that
         * they are held in
         */
        private ObjectState(Class<? extends MutableObject> clazz, Object[] constructionParams, ObjectId objectId) {
            this.clazz = clazz;
            this.constructionParams = new Object[constructionParams.length];
            for (int i=0; i<constructionParams.length; i++) {
                Object obj = constructionParams[i];
                if (obj instanceof MutableObject) {
                    this.constructionParams[i] = createObjectState((MutableObject) obj);
                }
                else this.constructionParams[i] = obj;
            }
            this.objectId = objectId;
        }

        public Object[] getConstructionParams() {
            return constructionParams;
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
            return objectId.hashCode();
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
