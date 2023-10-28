package net.scoreworks.treetools.commits;

import net.scoreworks.treetools.Remote;
import org.apache.commons.collections4.MapUtils;

import java.util.Map;

/**
 * A {@link Commit} that saves records normally but behaves inverted to the outside of the class (creations become changes and
 * vice versa, changes are inverted).
 */
public class InvertedCommit extends Commit {

    public InvertedCommit(Commit commit) {
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
}