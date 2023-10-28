package net.scoreworks.test_model;

import net.scoreworks.collection.HasDuration;

import net.scoreworks.treetools.MappedChild;
import org.jetbrains.annotations.NotNull;


public class Beam extends MappedChild<Voice, Long> implements Comparable<Beam>, HasDuration<Long> {
	//effectively final
	private Long endTick;

	//=====TRANSACTIONAL============================================================================
	private Beam(Voice voice, Long startTick) {
		super(voice, startTick);
		voice.beams.put(startTick, this);
	}

	protected void removeFromOwner() {
		getOwner().beams.remove(getKey());
	}

	//=====FUNCTIONAL===============================================================================

	public Beam(Voice voice, Long startTick, Long endTick) {
		super(voice, startTick);
		this.endTick = endTick;
		voice.beams.put(startTick, this);
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
