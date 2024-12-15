package net.scoreworks.test_model;

import net.scoreworks.collection.HasDuration;

import net.scoreworks.treetools.MappedChild;
import net.scoreworks.treetools.annotations.TransactionalConstructor;
import org.jetbrains.annotations.NotNull;


public class Beam extends MappedChild<Voice, Long> implements Comparable<Beam>, HasDuration<Long> {
	//effectively final
	private Long endTick;

	//=====TRANSACTIONAL============================================================================
	@TransactionalConstructor
	private Beam(Voice voice, Long startTick) {
		super(voice, startTick);
	}

	protected void removeFromOwner() {
		getOwner().beams.remove(getKey());
	}
	protected void addToOwner() {
		getOwner().beams.put(getKey(), this);
	}

	//=====FUNCTIONAL===============================================================================

	public Beam(Voice voice, Long startTick, Long endTick) {
		super(voice, startTick);
		this.endTick = endTick;
	}

	@Override
	public Long getEndKey() {
		return endTick;
	}

	@Override
	public Long getDuration() {
		return getEndKey()-getKey();
	}

	@Override
	public int compareTo(@NotNull Beam beam) {
		return (int) (this.getKey() - beam.getKey());
	}
}
