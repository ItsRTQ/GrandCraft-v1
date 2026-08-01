package com.hrtq.grandcraft.combat;

/**
 * One actor's stamina, owned by its {@link CombatController} the same way
 * {@link StaggerTracker} is.
 *
 * <p>Holds no reference to its settings: the controller resolves the live
 * {@link StaminaSettings} from the profile and passes them in, so a config change
 * takes effect on the next tick with nothing to invalidate.
 *
 * <h2>Exhaustion</h2>
 * Reaching zero is not the same as merely being low. An exhausted actor cannot act
 * at all, waits {@link CombatConstants#EXHAUSTION_RECOVERY_TICKS} before regen even
 * starts, and stays exhausted until the pool is back to
 * {@link CombatConstants#EXHAUSTION_CLEAR_FRACTION} of its maximum. That gap is
 * what makes "do not spend your last point" a real decision rather than a rounding
 * error — without it an actor would recover one point and immediately act again.
 */
public final class StaminaPool {
	/**
	 * The pool starts full, but its size comes from settings the constructor cannot
	 * see, so it is filled on first use instead. Tracked with a flag rather than a
	 * sentinel value because an empty pool is a legitimate state.
	 *
	 * <p>Every settings-taking method fills it, not just {@link #tick}: an entity can
	 * be handed a controller at load and act before its own first tick runs, and a
	 * pool still reading zero at that moment would refuse the action.
	 */
	private boolean initialised;

	/** Held as a float so a per-tick share of a per-second rate stays exact. */
	private float current;

	/** Ticks before regen resumes. Refreshed by every spend. */
	private int regenDelay;

	private boolean exhausted;

	/**
	 * Extra ceiling this actor has bought with attribute points.
	 *
	 * <p>Held here rather than passed to each method, and rather than folded into a
	 * per-player copy of {@link StaminaSettings}. A per-player settings record is what
	 * {@code StaminaScaling} exists to avoid, for a reason that does not apply to this
	 * one — an absolute bonus does not round away the way a percentage does — but the
	 * allocation per tick would be real. Passing it into all five settings-taking
	 * methods would work and is exactly the sort of thing one call site forgets, which
	 * would leave the ceiling and the exhaustion threshold disagreeing.
	 *
	 * <p>Set by the controller when the points change; zero for anything without them.
	 */
	private int bonusMax;

	public float current() {
		return this.current;
	}

	/**
	 * Sets the bought ceiling. Idempotent, and safe to call every tick.
	 *
	 * <p>Lowering it does not take stamina away on the spot — {@link #fill} trims the
	 * pool to the new ceiling on its next pass, which is the same treatment a config
	 * change already gets.
	 */
	public void setBonusMax(int bonus) {
		this.bonusMax = Math.max(bonus, 0);
	}

	/**
	 * This actor's real ceiling: what the config says, plus what it bought.
	 *
	 * <p>Every read of the maximum goes through here — regen, the exhaustion threshold
	 * and the fill trim alike — so there is no way for them to disagree about how big
	 * the pool is.
	 */
	public float max(StaminaSettings settings) {
		return settings.maxStamina() + this.bonusMax;
	}

	public boolean exhausted() {
		return this.exhausted;
	}

	/**
	 * Ticks still to wait before regen resumes. Sent to the client, which needs the
	 * remaining delay rather than the configured one to carry the value forward.
	 */
	public int regenDelay() {
		return this.regenDelay;
	}

	/** Whether this actor could pay for an action costing {@code cost} right now. */
	public boolean has(StaminaSettings settings, float cost) {
		fill(settings);
		return !this.exhausted && this.current >= cost;
	}

	/**
	 * Advances regen. Called once per tick, and only for actors that use stamina.
	 *
	 * <p>{@code regenMultiplier} is whatever the actor's stats do to the configured
	 * rate — 1.0 for anything without stats. It arrives as a bare number rather than
	 * as adjusted settings so this pool stays ignorant of where it came from, in
	 * keeping with it holding no settings of its own.
	 */
	public void tick(StaminaSettings settings, float regenMultiplier) {
		fill(settings);

		if (this.regenDelay > 0) {
			this.regenDelay--;
			return;
		}

		float max = max(settings);

		if (this.current < max) {
			this.current = Math.min(max, this.current + settings.regenPerTick() * regenMultiplier);
		}

		// Checked outside the branch above: a pool already at or above the threshold —
		// because the ceiling was lowered, say — would otherwise stay flagged as
		// exhausted with no regen left to run and nothing to clear it.
		if (this.exhausted && this.current >= max * CombatConstants.EXHAUSTION_CLEAR_FRACTION) {
			this.exhausted = false;
		}
	}

	/**
	 * Pays for a discrete action.
	 *
	 * @return false when the actor could not afford it, in which case nothing is
	 *         deducted and the caller must suppress the action.
	 */
	public boolean spend(StaminaSettings settings, float cost) {
		if (!has(settings, cost)) {
			return false;
		}

		this.current = Math.max(0.0F, this.current - cost);
		afterSpending(settings);
		return true;
	}

	/**
	 * Pays for a continuous cost, taking whatever is left if the amount exceeds it.
	 *
	 * <p>Unlike {@link #spend} this never refuses — the caller for a continuous cost
	 * is something already happening, such as a sprint in progress, and stopping it
	 * is the caller's job once {@link #exhausted()} turns true.
	 */
	public void drain(StaminaSettings settings, float amount) {
		if (amount <= 0.0F) {
			return;
		}

		fill(settings);
		this.current = Math.max(0.0F, this.current - amount);
		afterSpending(settings);
	}

	/**
	 * Fills the pool on first use, and keeps it under a ceiling that may since have
	 * been lowered — by an admin editing the config, or by a later feature changing
	 * what an actor's maximum is.
	 */
	private void fill(StaminaSettings settings) {
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

	private void afterSpending(StaminaSettings settings) {
		this.regenDelay = settings.regenDelayTicks();

		if (this.current > 0.0F) {
			return;
		}

		this.current = 0.0F;
		this.exhausted = true;

		// Whichever wait is longer. Taking the max rather than replacing means a long
		// configured delay is never shortened by bottoming out.
		this.regenDelay = Math.max(this.regenDelay, CombatConstants.EXHAUSTION_RECOVERY_TICKS);
	}
}
