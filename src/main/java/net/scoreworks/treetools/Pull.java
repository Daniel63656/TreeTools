/*
 * Copyright (c) 2023 Daniel Maier.
 * Licensed under the MIT License.
 */

package net.scoreworks.treetools;

import net.scoreworks.treetools.commits.Commit;
import net.scoreworks.treetools.exceptions.TransactionException;

import java.lang.reflect.Field;
import java.util.*;

import static net.scoreworks.treetools.TransactionManager.verbose;


public class Pull {
    private final Set<Remote.ObjectState> creationChores;
    private final Map<Remote.ObjectState, Remote.ObjectState> changeChores;
    private final Remote remote;

    /**
     * Pull one commit by applying its changes to both the data model and the corresponding {@link Remote}
     * @param commit the commit to be pulled
     */
    public Pull(Repository repository, Commit commit) {
        repository.ongoingPull = true;
        //copy creation- and changeRecords to get collections to cross things off
        creationChores = new HashSet<>(commit.getCreationRecords());
        changeChores = new HashMap<>(commit.getChangeRecords());
        Set<Remote.ObjectState> deletionChores = commit.getDeletionRecords();   //here no copying required
        remote = repository.remote;

        //DELETION - assumes deletion records are present in all subsequent children, so their wrappers get also notified
        for (Remote.ObjectState entry : deletionChores) {
            if (verbose) System.out.println(">deleting "+entry.clazz.getSimpleName()+"["+entry.hashCode()+"]");
            Child<?> objectToDelete = (Child<?>) remote.get(entry);
            MutableObject owner = objectToDelete.getOwner();
            objectToDelete.removeFromOwner();
            objectToDelete.notifyAndRemoveRegisteredWrappers();  //notify own wrapper about deletion
            owner.notifyRegisteredWrappersAboutChange();  //notify owners' wrapper (necessary because only removeFromOwner() called)
            remote.removeValue(objectToDelete);
        }

        // loop over creation and change records and update the remote to contain all new states and objects
        //CREATION - Recursion possible because of dependency on owner and keys
        Remote.ObjectState creationRecord;
        while (!creationChores.isEmpty()) {
            creationRecord = creationChores.iterator().next();
            try {
                pullCreationRecord(creationRecord);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        //CHANGE - No recursion
        Map.Entry<Remote.ObjectState, Remote.ObjectState> changeRecord;
        while (!changeChores.isEmpty()) {
            changeRecord = changeChores.entrySet().iterator().next();
            try {
                pullChangeRecord(changeRecord.getKey(), changeRecord.getValue());
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        //at last, apply the actual changes when all objects are created and accessible via remote
        try {
            for (Remote.ObjectState state : commit.getCreationRecords()) {
                applyState(state);
            }
            for (Remote.ObjectState state : commit.getChangeRecords().values()) {
                applyState(state);
                remote.get(state).notifyRegisteredWrappersAboutChange();
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        //finalize the pull
        if (commit.getCommitId() != null)
            repository.currentCommitId = commit.getCommitId();
        repository.ongoingPull = false;
    }

    /**
     * creates an object from a creation record and put its key into the {@link Remote}
     * @param objKey key of the object to be created
     */
    private void pullCreationRecord(Remote.ObjectState objKey) throws IllegalAccessException {
        if (verbose) System.out.println(">creating "+objKey.clazz.getSimpleName()+"["+objKey.hashCode()+"]");
        //parse construction params into array
        Object[] constructionParams = objKey.getConstructionParams();
        Object[] params = new Object[constructionParams.length];
        for (int i=0; i<constructionParams.length; i++) {
            Object key = constructionParams[i];
            //MutableObjects need to be resolved to their objects which may need to be created/changed themselves
            if (key instanceof Remote.ObjectState) {
                Remote.ObjectState state = (Remote.ObjectState) key;
                if (creationChores.contains(state)) {
                    pullCreationRecord(state);
                }
                //check if state exists in changeRecords as after
                else if (changeChores.containsValue(state)) {
                    pullChangeRecord(state, changeChores.get(state));
                }
                //now object can be safely accessed via remote
                params[i] = remote.get(key);
                if (params[i] == null)
                    throw new TransactionException("remote didn't contain "+state.clazz.getSimpleName()+" with id["+key.hashCode()+"] needed during creation of "+objKey.clazz.getSimpleName(), objKey.hashCode());
            }
            //object is immutable, no parsing needed
            else {
                params[i] = key;
            }
        }
        //construct the object
        Child<?> objectToCreate = ClassMetadata.construct(objKey.clazz, params);
        remote.put(objKey, objectToCreate);
        creationChores.remove(objKey);
    }

    private void pullChangeRecord(Remote.ObjectState before, Remote.ObjectState after) throws IllegalAccessException {
        if (verbose) System.out.println(">changing "+before.clazz.getSimpleName()+"["+before.hashCode()+"] -> ["+after.hashCode()+"]");
        MutableObject objectToChange = remote.get(before);
        if (objectToChange == null)
            throw new TransactionException("remote didn't contain "+before.clazz.getSimpleName(), before.hashCode());
        remote.put(after, objectToChange);
        changeChores.remove(after);
    }

    private void applyState(Remote.ObjectState state) throws IllegalAccessException {
        MutableObject mo = remote.get(state);
        for (Field field : ClassMetadata.getFields(mo)) {
            if (state.getFields().containsKey(field)) {
                field.setAccessible(true);
                Object value = state.getFields().get(field);
                if (value instanceof Remote.ObjectState) {
                    Remote.ObjectState referencedState = (Remote.ObjectState) value;
                    MutableObject referencedObject = remote.get(referencedState);
                    if (referencedObject == null)
                        throw new TransactionException("can't find "+referencedState.clazz.getSimpleName()+"["+referencedState.hashCode()+"] in remote, cross referenced by "+remote.getKey(mo).clazz.getSimpleName(), remote.getKey(mo).hashCode());
                    field.setAccessible(true);
                    try {
                        field.set(mo, referencedObject);
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
                else field.set(mo, value);
            }
        }
    }
}
