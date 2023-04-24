package com.immersive.transactions.commits;

import com.immersive.transactions.CommitId;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import com.immersive.transactions.Remote.ObjectState;

import java.util.Map;

public class CollapsedCommit extends Commit {
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
                deletionRecords.put(beforeState, entry.getValue());
                changeRecords.removeValue(deleteState);

                //trace back
                Object[] objects = deletionRecords.get(beforeState);
                for (int i=0; i<objects.length; i++) {
                    if (entry.getValue()[i] instanceof ObjectState) {
                        entry.getValue()[i] = traceBack((ObjectState) entry.getValue()[i]);
                    }
                }
            }
            //deletion is in creationRecords
            else if (creationRecords.containsKey(deleteState)) {
                creationRecords.remove(deleteState);
            }
            else if (deletionRecords.containsKey(deleteState))
                throw new RuntimeException("Tried to delete an object that is already deleted!");
                //not contained so far
            else {
                deletionRecords.put(deleteState, entry.getValue());

                //trace back
                Object[] objects = deletionRecords.get(deleteState);
                for (int i=0; i<objects.length; i++) {
                    if (entry.getValue()[i] instanceof ObjectState) {
                        entry.getValue()[i] = traceBack((ObjectState) entry.getValue()[i]);
                    }
                }
            }
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


        //handle key was referenced in params of creation records
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
}
