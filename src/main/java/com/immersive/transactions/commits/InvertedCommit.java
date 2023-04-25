package com.immersive.transactions.commits;

import com.immersive.transactions.CommitId;
import com.immersive.transactions.Remote;
import org.apache.commons.collections4.MapUtils;

import java.util.Map;

/**
 * A {@link CollapsedCommit} that saves records normally but behaves inverted to the outside of the class (creations become changes and
 * vice versa, changes are inverted).
 */
public class InvertedCommit extends CollapsedCommit {

    public InvertedCommit(CollapsedCommit commit) {
        super(commit);
    }

    public Map<Remote.ObjectState, Object[]> getDeletionRecords() {
        return MapUtils.unmodifiableMap(creationRecords);
    }
    public Map<Remote.ObjectState, Object[]> getCreationRecords() {
        return MapUtils.unmodifiableMap(deletionRecords);
    }
    public Map<Remote.ObjectState, Remote.ObjectState> getChangeRecords() {
        return MapUtils.unmodifiableMap(changeRecords.inverseBidiMap());
    }
    public Map<Remote.ObjectState, Remote.ObjectState> getInvertedChangeRecords() {
        return MapUtils.unmodifiableMap(changeRecords);
    }

    public Remote.ObjectState traceBack(Remote.ObjectState state) {
        while (uncutChanges.containsKey(state))
            state = uncutChanges.get(state);
        return state;
    }

    public Remote.ObjectState traceForward(Remote.ObjectState state) {
        while (uncutChanges.containsValue(state))
            state = uncutChanges.getKey(state);
        return state;
    }
}