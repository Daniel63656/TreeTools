package com.immersive.test_model;

import com.immersive.collection.HasDuration;
import com.immersive.transactions.DoubleKeyedChildEntity;

import org.jetbrains.annotations.NotNull;


public class Beam extends DoubleKeyedChildEntity<Voice, Long> implements Comparable<Beam>, HasDuration<Long> {

	//=====TRANSACTIONAL============================================================================
	public Beam(Voice voice, Long startTick, Long endTick) {
		super(voice, startTick, endTick);
		voice.beams.put(startTick, this);
	}

	protected void onRemove() {
		getOwner().beams.remove(getKey());
	}

	//=====FUNCTIONAL===============================================================================

	@Override
	public Long getDuration() {
		return getEndKey()-getKey();
	}

	@Override
	public int compareTo(@NotNull Beam beam) {
		return (int) (this.getKey() - beam.getKey());
	}
}
