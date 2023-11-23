package net.scoreworks.treetools.commits;

import net.scoreworks.treetools.Remote;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.SetUtils;

import java.util.Map;
import java.util.Set;

/**
 * A {@link Commit} that saves records normally but behaves inverted to the outside of the class (creations become deletions and
 * vice versa, changes are inverted).
 */
public class InvertedCommit extends Commit {

    public InvertedCommit(Commit commit) {
        super(commit);
    }

    public Set<Remote.ObjectState> getDeletionRecords() {
        return SetUtils.unmodifiableSet(creationRecords);
    }
    public Set<Remote.ObjectState> getCreationRecords() {
        return SetUtils.unmodifiableSet(deletionRecords);
    }
    public Map<Remote.ObjectState, Remote.ObjectState> getChangeRecords() {
        return MapUtils.unmodifiableMap(changeRecords.inverseBidiMap());
    }
    public Map<Remote.ObjectState, Remote.ObjectState> getInvertedChangeRecords() {
        return MapUtils.unmodifiableMap(changeRecords);
    }
}