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
        //copy modificationRecords to safely cross things off without changing commit itself!
        creationChores = new HashMap<>(commit.getCreationRecords());
        Map<Remote.ObjectState, Object[]> deletionChores = commit.getDeletionRecords();  //no removing
        changeChores = new HashMap<>(commit.getInvertedChangeRecords()); //map changeChores with AFTER as key!
        remote = repository.remote;

        //DELETION - Assumes Deletion Records are created for all subsequent children!!!
        for (Map.Entry<Remote.ObjectState, Object[]> entry : deletionChores.entrySet()) {
            if (verbose) System.out.println(">deleting "+entry.getKey().clazz.getSimpleName()+"["+entry.getKey().hashCode()+"]");
            ChildEntity<?> objectToDelete = (ChildEntity<?>) remote.get(entry.getKey());
            objectToDelete.onCleared();
            objectToDelete.getOwner().onChanged();
            objectToDelete.destruct();
            remote.removeValue(objectToDelete);
        }

        //CREATION - Recursion possible because of dependency on owner and cross-references
        Map.Entry<Remote.ObjectState, Object[]> creationRecord;
        while (!creationChores.isEmpty()) {
            creationRecord = creationChores.entrySet().iterator().next();
            try {
                pullCreationRecord(creationRecord.getKey(), creationRecord.getValue());
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        //CHANGE - Recursion possible because of dependency on cross-references
        Map.Entry<Remote.ObjectState, Remote.ObjectState> changeRecord;
        while (!changeChores.isEmpty()) {
            changeRecord = changeChores.entrySet().iterator().next();
            try {
                pullChangeRecord(changeRecord.getValue(), changeRecord.getKey());
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        //at last link the open cross-reference dependencies
        for (CrossReferenceToDo cr : crossReferences) {

            //cross-referenced-states only point at valid states in the remote when this state was constructed.
            //underlying objects and their states may have changed by now, so we need to trace their changes through
            //all commits that happened after states' construction up to this commit
            //TODO is this access thread safe???
            for (Commit c : TransactionManager.getInstance().commits.subMap(cr.state.creationId, false, commit.getCommitId(), true).values()) {
                cr.crossReferencedState = c.traceForward(cr.crossReferencedState);
            }

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
                //check if state exists in changeRecords (now mapped as <after, before>
                else if (changeChores.containsKey(state)) {
                    pullChangeRecord(changeChores.get(state), state);
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
        //construct the object
        ChildEntity<?> objectToCreate = DataModelInfo.construct(objKey.clazz, params);
        imprintLogicalContentOntoObject(objKey, objectToCreate);
        //notify owner that a new child was created
        objectToCreate.getOwner().onChanged();
        //System.out.println("PULL: created key "+objKey.hashCode());
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
        objectToChange.onChanged();
        //if (verbose) System.out.println("PULL: changed from "+before.hashCode()+" to "+after.hashCode());
        remote.put(after, objectToChange);
        changeChores.remove(after);
    }

    private void imprintLogicalContentOntoObject(Remote.ObjectState state, MutableObject dme) throws IllegalAccessException {
        for (Field field : DataModelInfo.getContentFields(dme)) {
            if (state.containsKey(field)) {
                field.setAccessible(true);
                field.set(dme, state.get(field));
            }
            //field is a cross-reference
            else if (state.crossReferences.containsKey(field)) {
                if (state.crossReferences.get(field) != null) {
                    //save cross-references to do at the very end to avoid infinite recursion when cross-references point at each other!
                    crossReferences.add(new CrossReferenceToDo(dme, state, state.crossReferences.get(field), field));
                }
                else {
                    field.setAccessible(true);
                    field.set(dme, null);
                }
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