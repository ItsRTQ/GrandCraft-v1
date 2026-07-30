package com.hrtq.grandcraft.combat;

/**
 * Tracks consecutive stagger-producing hits so repeated hits cannot stun lock.
 *
 * <p>First hit staggers normally, the second more weakly, the third and beyond
 * not at all. The sequence resets only after {@link CombatTuning#staggerResetTicks()}
 * without a qualifying hit — every qualifying hit restarts that countdown,
 * including the suppressed ones, so continuous pressure never re-earns a strong
 * stagger.
 */
public final class StaggerTracker {
	private int level;
	private int ticksSinceLastHit;

	/** Advances the reset countdown. Called once per tick by the controller. */
	public void tick() {
		if (this.level == 0) {
			return;
		}

		this.ticksSinceLastHit++;

		if (this.ticksSinceLastHit >= CombatTuning.staggerResetTicks()) {
			this.level = 0;
			this.ticksSinceLastHit = 0;
		}
	}

	/**
	 * Registers a qualifying hit.
	 *
	 * @return the stagger level to apply, or 0 when the entity has already
	 *         absorbed enough consecutive hits that only damage should remain.
	 */
	public int registerHit() {
		this.ticksSinceLastHit = 0;

		if (this.level >= CombatTuning.MAX_STAGGER_LEVEL) {
			return 0;
		}

		return ++this.level;
	}
}
