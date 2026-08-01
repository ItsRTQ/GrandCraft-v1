package com.hrtq.grandcraft.stats;

/**
 * One character's mana, owned by its {@code CombatController} the same way the
 * stamina pool is.
 *
 * <p>Holds no reference to its settings: the controller resolves the live
 * {@link ManaSettings} and passes them in, so a config change takes effect on the
 * next tick with nothing to invalidate.
 *
 * <h2>No exhaustion, on purpose</h2>
 * Stamina has an exhaustion state because emptying it must be a punished mistake —
 * that gap is what makes "do not spend your last point" a decision under pressure.
 * Mana has no pressure to create until abilities exist, so inventing a lockout rule
 * now would be a rule with no mechanic behind it. Whatever running out of mana
 * should cost is a decision for the slice that lets you spend it.
 *
 * <h2>Transient, and when that stops being right</h2>
 * The pool is not persisted, matching the stamina pool and the controller that holds
 * both. That is safe only because nothing spends mana: a value that is always full
 * loses nothing by being rebuilt on login.
 *
 * <p><strong>Revisit this the moment an ability spends mana.</strong> At that point
 * logging out empty and back in full is a free refill, and this pool needs to move
 * to a persistent attachment.
 */
public final class ManaPool {
	/**
	 * The pool starts full, but its size comes from settings the constructor cannot
	 * see, so it is filled on first use instead. Tracked with a flag rather than a
	 * sentinel value because an empty pool is a legitimate state.
	 */
	private boolean initialised;

	/** Held as a float so a per-tick share of a per-second rate stays exact. */
	private float current;

	/** Ticks before regen resumes. Refreshed by every spend. */
	private int regenDelay;

	/**
	 * Extra ceiling bought with attribute points. Held here for the same reasons the
	 * stamina pool holds its own — see {@code StaminaPool.bonusMax}.
	 */
	private int bonusMax;

	public float current() {
		return this.current;
	}

	/** Sets the bought ceiling. Idempotent, and safe to call every tick. */
	public void setBonusMax(int bonus) {
		this.bonusMax = Math.max(bonus, 0);
	}

	/**
	 * This character's real ceiling: what the config says, plus what they bought. Every
	 * read of the maximum goes through here so none of them can disagree.
	 */
	public float max(ManaSettings settings) {
		return settings.maxMana() + this.bonusMax;
	}

	/**
	 * Ticks still to wait before regen resumes. Sent to the client, which needs the
	 * remaining delay rather than the configured one to carry the value forward.
	 */
	public int regenDelay() {
		return this.regenDelay;
	}

	/** Whether this character could pay for something costing {@code cost} right now. */
	public boolean has(ManaSettings settings, float cost) {
		fill(settings);
		return this.current >= cost;
	}

	/** Advances regen. Called once per tick, and only for characters that use mana. */
	public void tick(ManaSettings settings) {
		fill(settings);

		if (this.regenDelay > 0) {
			this.regenDelay--;
			return;
		}

		float max = max(settings);

		if (this.current < max) {
			this.current = Math.min(max, this.current + settings.regenPerTick());
		}
	}

	/**
	 * Pays for a discrete cost.
	 *
	 * @return false when the character could not afford it, in which case nothing is
	 *         deducted and the caller must suppress whatever it was paying for.
	 */
	public boolean spend(ManaSettings settings, float cost) {
		if (!has(settings, cost)) {
			return false;
		}

		this.current = Math.max(0.0F, this.current - cost);
		this.regenDelay = settings.regenDelayTicks();
		return true;
	}

	/**
	 * Fills the pool on first use, and keeps it under a ceiling that may since have
	 * been lowered — by an admin editing the config, or by Arcane coming to drive the
	 * maximum.
	 */
	private void fill(ManaSettings settings) {
		float max = max(settings);

		if (!this.initialised) {
			this.current = max;
			this.initialised = true;
			return;
		}

		if (this.current > max) {
			this.current = max;
		}
	}
}
