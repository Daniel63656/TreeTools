package com.immersive.test_model;

import com.immersive.collection.HasDuration;
import com.immersive.transactions.DoubleKeyedChildEntity;

import org.jetbrains.annotations.NotNull;

import java.util.List;


public class Beam extends DoubleKeyedChildEntity<Voice, Long> implements Comparable<Beam>, HasDuration {

	//=====TRANSACTIONAL============================================================================
	public Beam(Voice voice, Long startTick, Long endTick) {
		super(voice, startTick, endTick);
		voice.beams.put(startTick, this);
	}

	private void destruct() {
		getOwner().beams.remove(getKey());
	}

	//=====FUNCTIONAL===============================================================================

	@Override
	public int getDuration() {
		return (int) (getEndKey()-getKey());
	}

	@Override
	public int compareTo(@NotNull Beam beam) {
		return (int) (this.getKey() - beam.getKey());
	}
}
