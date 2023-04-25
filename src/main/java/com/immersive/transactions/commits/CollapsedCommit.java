package com.immersive.transactions.commits;

import com.immersive.transactions.CommitId;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import com.immersive.transactions.Remote.ObjectState;

import java.util.Map;


/**
 * A {@link Commit} that summarizes the effects of several commits into one.
 */
public class CollapsedCommit extends Commit {

    /**
     * collapsing commits can destroy change information (by overriding with deletion or creation), but these changeRecords
     * may be needed to trace cross-referenced states to the start/end of the {@link CollapsedCommit}. This field makes
     * this possible by providing an uncut record of all encountered changes
     */
    final DualHashBidiMap<ObjectState, ObjectState> uncutChanges;

    /**
     * create an empty commit that collapses commits added with {@link CollapsedCommit#add(Commit)} into one commit.
     * Creation of this commit does not increment the {@link CommitId}
     */
    public CollapsedCommit() {
        super();
        uncutChanges = new DualHashBidiMap<>();
    }

    public CollapsedCommit(CollapsedCommit commit) {
        super(commit.deletionRecords, commit.creationRecords, commit.changeRecords);
        uncutChanges = commit.uncutChanges;
    }

    public ObjectState traceBack(ObjectState state) {
        while (uncutChanges.containsValue(state))
            state = uncutChanges.getKey(state);
        return state;
    }

    public ObjectState traceForward(ObjectState state) {
        while (uncutChanges.containsKey(state))
            state = uncutChanges.get(state);
        return state;
    }

    public void add(Commit commit) {
        for (Map.Entry<ObjectState, Object[]> entry : commit.creationRecords.entrySet()) {
            ObjectState creationState = entry.getKey();
            if (creationRecords.containsKey(creationState) ||
                    deletionRecords.containsKey(creationState) ||
                    changeRecords.containsValue(creationState))
                throw new RuntimeException("Tried to create an object already present in commit!");
            creationRecords.put(creationState, entry.getValue());
        }

        for (Map.Entry<ObjectState, Object[]> entry : commit.deletionRecords.entrySet()) {
            ObjectState deleteState = entry.getKey();
            //deletion is contained as after key in a change
            if (changeRecords.containsValue(deleteState)) {
                ObjectState beforeState = changeRecords.getKey(deleteState);
                putInDeletionRecord(beforeState, entry.getValue());
                changeRecords.removeValue(deleteState);
            }
            //deletion is in creationRecords
            else if (creationRecords.containsKey(deleteState)) {
                creationRecords.remove(deleteState);
            }
            else if (deletionRecords.containsKey(deleteState))
                throw new RuntimeException("Tried to delete an object that is already deleted!");
            //not contained so far
            else putInDeletionRecord(deleteState, entry.getValue());
        }

        for (Map.Entry<ObjectState, ObjectState> entry : commit.changeRecords.entrySet()) {
            ObjectState before = entry.getKey();
            ObjectState after = entry.getValue();
            //add to detailed change list
            uncutChanges.put(before, after);
            //change was considered a creation so far - update creation record
            if (creationRecords.containsKey(before)) {
                creationRecords.put(after, creationRecords.get(before));
                creationRecords.remove(before);
            }
            else if (deletionRecords.containsKey(before))
                throw new RuntimeException("Tried to change an object that is already deleted!");
                //change is contained as after key in existing change - update change record
            else if (changeRecords.containsValue(before))
                changeRecords.put(changeRecords.getKey(before), after);
                //not contained so far
            else
                changeRecords.put(before, entry.getValue());
        }
        //if change affects a construction parameter of a changeRecord, make sure they point to the state after the change
        for (Object[] objects : creationRecords.values()) {
            for (int i=0; i<objects.length; i++) {
                if (objects[i] instanceof ObjectState) {
                    ObjectState state = (ObjectState) objects[i];
                    if (commit.changeRecords.containsKey(state))
                        objects[i] = commit.changeRecords.get(state);
                }
            }
        }
    }
    
    private void putInDeletionRecord(ObjectState deleteState, Object[] constructionParams) {
        deletionRecords.put(deleteState, constructionParams);
        //trace deletionRecords construction parameters back to the start of the commit
        Object[] objects = deletionRecords.get(deleteState);
        for (int i=0; i<objects.length; i++) {
            if (constructionParams[i] instanceof ObjectState) {
                constructionParams[i] = traceBack((ObjectState) constructionParams[i]);
            }
        }
    }
}