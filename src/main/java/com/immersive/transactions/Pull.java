package com.immersive.transactions;

import com.immersive.transactions.commits.Commit;
import com.immersive.transactions.exceptions.TransactionException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.immersive.transactions.TransactionManager.verbose;


public class Pull {
    private final Map<Remote.ObjectState, Object[]> creationChores;
    private final Map<Remote.ObjectState, Remote.ObjectState> changeChores;
    private final Remote remote;
    private final List<CrossReferenceToDo> crossReferences = new ArrayList<>();

    /**
     * pull one commit from the remote and apply its changes to the data model
     * @param commit the commit to be pulled
     */
    public Pull(Repository repository, Commit commit) {
        repository.ongoingPull = true;
        //copy modification- and changeRecords to safely cross things off without changing the commit itself
        creationChores = new HashMap<>(commit.getCreationRecords());
        changeChores = new HashMap<>(commit.getChangeRecords());
        //no removing here
        Map<Remote.ObjectState, Object[]> deletionChores = commit.getDeletionRecords();
        remote = repository.remote;

        //DELETION - assumes deletion records are present in all subsequent children, so their wrappers get also notified
        for (Map.Entry<Remote.ObjectState, Object[]> entry : deletionChores.entrySet()) {
            if (verbose) System.out.println(">deleting "+entry.getKey().clazz.getSimpleName()+"["+entry.getKey().hashCode()+"]");
            ChildEntity<?> objectToDelete = (ChildEntity<?>) remote.get(entry.getKey());
            MutableObject owner = objectToDelete.getOwner();
            objectToDelete.onRemove();
            objectToDelete.notifyAndRemoveRegisteredWrappers();  //notify own wrapper about deletion
            owner.notifyRegisteredWrappersAboutChange();  //notify owners' wrapper (necessary because only onRemove() called)

            remote.removeValue(objectToDelete);
        }

        //CREATION - Recursion possible because of dependency on owner and keys
        Map.Entry<Remote.ObjectState, Object[]> creationRecord;
        while (!creationChores.isEmpty()) {
            creationRecord = creationChores.entrySet().iterator().next();
            try {
                pullCreationRecord(creationRecord.getKey(), creationRecord.getValue());
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        //CHANGE
        Map.Entry<Remote.ObjectState, Remote.ObjectState> changeRecord;
        while (!changeChores.isEmpty()) {
            changeRecord = changeChores.entrySet().iterator().next();
            try {
                pullChangeRecord(changeRecord.getKey(), changeRecord.getValue());
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        //at last link the open cross-reference dependencies
        for (CrossReferenceToDo cr : crossReferences) {
            MutableObject referencedObject = remote.get(cr.crossReferencedState);
            if (referencedObject == null)
                throw new TransactionException("can't find "+cr.crossReferencedState.clazz.getSimpleName()+"["+cr.crossReferencedState.hashCode()+"] in remote, cross referenced by "+remote.getKey(cr.dme).clazz.getSimpleName(), remote.getKey(cr.dme).hashCode());
            cr.objectField.setAccessible(true);
            try {
                cr.objectField.set(cr.dme, referencedObject);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        crossReferences.clear();
        if (commit.getCommitId() != null)
            repository.currentCommitId = commit.getCommitId();
        repository.ongoingPull = false;
    }

    /**
     * creates an object from a creation record and put its key into the {@link Remote}
     * @param objKey key of the object to be created
     * @param constructionParams list of objects needed for construction. These are {@link Remote.ObjectState}s if
     *                           the param is another {@link MutableObject} or an immutable object itself
     */
    private void pullCreationRecord(Remote.ObjectState objKey, Object[] constructionParams) throws IllegalAccessException {
        if (verbose) System.out.println(">creating "+objKey.clazz.getSimpleName()+"["+objKey.hashCode()+"]");
        //parse construction params into array
        Object[] params = new Object[constructionParams.length];
        for (int i=0; i<constructionParams.length; i++) {
            Object key = constructionParams[i];
            //DMEs need to be resolved to their objects which may need to be created/changed themselves
            if (key instanceof Remote.ObjectState) {
                Remote.ObjectState state = (Remote.ObjectState) key;
                if (creationChores.containsKey(state)) {
                    pullCreationRecord(state, creationChores.get(state));
                }
                //check if state exists in changeRecords as after
                else if (changeChores.containsValue(state)) {
                    pullChangeRecord(state, changeChores.get(state));
                }
                //now object can be safely accessed via LOT
                params[i] = remote.get(key);
                if (params[i] == null)
                    throw new TransactionException("remote didn't contain "+state.clazz.getSimpleName()+" with id["+key.hashCode()+"] needed during creation of "+objKey.clazz.getSimpleName(), objKey.hashCode());
            }
            //object is an immutable, no parsing needed
            else {
                params[i] = key;
            }
        }
        //construct the object - this automatically notifies the wrappers of the owner
        ChildEntity<?> objectToCreate = DataModelInfo.construct(objKey.clazz, params);
        imprintLogicalContentOntoObject(objKey, objectToCreate);
        remote.put(objKey, objectToCreate);
        creationChores.remove(objKey);
    }

    private void pullChangeRecord(Remote.ObjectState before, Remote.ObjectState after) throws IllegalAccessException {
        if (verbose) System.out.println(">changing "+before.clazz.getSimpleName()+"["+before.hashCode()+"] -> ["+after.hashCode()+"]");
        MutableObject objectToChange = remote.get(before);
        if (objectToChange == null)
            throw new TransactionException("remote didn't contain "+before.clazz.getSimpleName(), before.hashCode());

        imprintLogicalContentOntoObject(after, objectToChange);
        //notify potential wrappers about the change
        objectToChange.notifyRegisteredWrappersAboutChange();
        remote.put(after, objectToChange);
        changeChores.remove(after);
    }

    private void imprintLogicalContentOntoObject(Remote.ObjectState state, MutableObject dme) throws IllegalAccessException {
        for (Field field : DataModelInfo.getFields(dme)) {
            if (state.getFields().containsKey(field)) {
                field.setAccessible(true);
                Object value = state.getFields().get(field);
                if (value instanceof Remote.ObjectState) {
                    //save cross-references to do at the very end to avoid infinite recursion when cross-references point at each other!
                    crossReferences.add(new CrossReferenceToDo(dme, state, (Remote.ObjectState) value, field));
                }
                else field.set(dme, state.getFields().get(field));
            }
        }
    }

    private static class CrossReferenceToDo {
        MutableObject dme;
        Remote.ObjectState state, crossReferencedState;
        Field objectField;
        CrossReferenceToDo(MutableObject dme, Remote.ObjectState state, Remote.ObjectState crossReferencedState, Field objectField) {
            this.dme = dme;
            this.state = state;
            this.crossReferencedState = crossReferencedState;
            this.objectField = objectField;
        }
    }
}