package com.hrtq.grandcraft.stats;

import com.hrtq.grandcraft.player.GrandCraftAttachments;
import net.minecraft.world.entity.LivingEntity;

/**
 * One character's mana.
 *
 * <p>Holds no state of its own and no reference to its settings: the pool lives on
 * the {@code GrandCraftAttachments.MANA} attachment and the controller passes in the
 * live {@link ManaSettings} and the bought ceiling, so a config change takes effect
 * on the next tick with nothing to invalidate.
 *
 * <h2>No exhaustion, on purpose</h2>
 * Stamina has an exhaustion state because emptying it must be a punished mistake —
 * that gap is what makes "do not spend your last point" a decision under pressure.
 * Mana has a different scarcity rule: it does not come back at all below the Arcane
 * threshold, so running dry is already a real cost without a lockout on top. Whether
 * casting on empty should hurt is a decision for the slice that gives it a reason to.
 *
 * <h2>Persistent, unlike the stamina pool</h2>
 * Statics over an attachment rather than fields on the controller, because the
 * controller is transient and mana is not. That divergence is deliberate and is the
 * consequence of mana being spendable and not universally recoverable: a pool that
 * refilled on login would let anyone below the threshold — who cannot refill any
 * other way — top up by quitting to the menu.
 *
 * <p>Taking the entity in every method is this file's own long-standing convention,
 * carried over from when it held the values directly.
 *
 * <h2>Absence means full</h2>
 * The attachment has no initializer, so a character who has never had mana read has
 * no attachment at all — and that is distinguishable from one whose pool is empty.
 * {@link #read} treats absence as "fill to maximum", which is what makes a new
 * character, and a freshly respawned one, start full.
 */
public final class ManaPool {
	private ManaPool() {
	}

	/**
	 * This character's real ceiling: what the config says, plus what they bought.
	 * Every read of the maximum goes through here so none of them can disagree.
	 */
	public static float max(ManaSettings settings, int bonusMax) {
		return settings.maxMana() + Math.max(bonusMax, 0);
	}

	public static float current(LivingEntity entity, ManaSettings settings, int bonusMax) {
		return read(entity, settings, bonusMax).current();
	}

	/**
	 * Ticks still to wait before regen resumes. Sent to the client, which needs the
	 * remaining delay rather than the configured one to carry the value forward.
	 */
	public static int regenDelay(LivingEntity entity, ManaSettings settings, int bonusMax) {
		return read(entity, settings, bonusMax).regenDelayTicks();
	}

	/** Whether this character could pay for something costing {@code cost} right now. */
	public static boolean has(LivingEntity entity, ManaSettings settings, int bonusMax, float cost) {
		return read(entity, settings, bonusMax).current() >= cost;
	}

	/**
	 * Advances regen. Called once per tick, and only for characters that use mana.
	 *
	 * @param regenMultiplier what this character's own recovery is worth, from
	 *        {@link StatSettings#manaRegenMultiplier}. Zero means mana does not come
	 *        back on its own at all, which is the normal state for anyone who has not
	 *        invested in Arcane.
	 */
	public static void tick(LivingEntity entity, ManaSettings settings, int bonusMax,
			float regenMultiplier, float bonusPerTick) {
		ManaState state = read(entity, settings, bonusMax);
		int delay = state.regenDelayTicks();

		// Natural recovery waits out the delay a spend imposed. A potion does not:
		// drinking one immediately after casting has to do something, or it reads as a
		// broken potion rather than as a delay nobody was told about. The delay is still
		// counted down underneath, so natural regen resumes on schedule.
		float natural = delay > 0 ? 0.0F : settings.regenPerTick() * regenMultiplier;
		float max = max(settings, bonusMax);
		float next = Math.min(max, state.current() + natural + bonusPerTick);

		// Nothing to write when nothing moves and there is no delay left to count down,
		// which is the common case: a full pool, or any character whose mana does not
		// recover. Writing regardless would mean an attachment update every tick for
		// every player forever.
		if (next == state.current() && delay == 0) {
			return;
		}

		write(entity, new ManaState(next, Math.max(0, delay - 1)));
	}

	/**
	 * Grants mana outright — a potion's instant chunk rather than a rate.
	 *
	 * <p>Imposes no regen delay and clears none: a gain is not a spend, and a drink
	 * should neither punish nor reward whatever the character was doing beforehand.
	 * Silently capped at the ceiling, so overdrinking wastes the remainder rather than
	 * banking it.
	 *
	 * @return how much was actually added, which is less than asked for on a nearly full
	 *         pool and zero on a full one
	 */
	public static float restore(LivingEntity entity, ManaSettings settings, int bonusMax,
			float amount) {
		if (amount <= 0.0F) {
			return 0.0F;
		}

		ManaState state = read(entity, settings, bonusMax);
		float next = Math.min(max(settings, bonusMax), state.current() + amount);

		if (next == state.current()) {
			return 0.0F;
		}

		write(entity, new ManaState(next, state.regenDelayTicks()));
		return next - state.current();
	}

	/**
	 * Pays for a discrete cost.
	 *
	 * @return false when the character could not afford it, in which case nothing is
	 *         deducted and the caller must suppress whatever it was paying for.
	 */
	public static boolean spend(LivingEntity entity, ManaSettings settings, int bonusMax, float cost) {
		ManaState state = read(entity, settings, bonusMax);

		if (state.current() < cost) {
			return false;
		}

		write(entity, new ManaState(
				Math.max(0.0F, state.current() - cost), settings.regenDelayTicks()));
		return true;
	}

	/**
	 * The stored pool, filled on first read and kept under a ceiling that may since
	 * have been lowered — by an admin editing the config, or by a reclass taking back
	 * the pool points that raised it.
	 *
	 * <p>Writes back in both of those cases rather than trimming only in memory: a
	 * value that read as trimmed but saved as its old self would come back on the next
	 * login, which is the same free refill this whole file exists to prevent.
	 */
	private static ManaState read(LivingEntity entity, ManaSettings settings, int bonusMax) {
		float max = max(settings, bonusMax);
		ManaState state = entity.getAttached(GrandCraftAttachments.MANA);

		// No attachment means never filled, not empty. The pool starts full and its
		// size comes from settings no constructor could have seen.
		if (state == null) {
			ManaState filled = new ManaState(max, 0);
			write(entity, filled);
			return filled;
		}

		if (state.current() > max) {
			ManaState trimmed = new ManaState(max, state.regenDelayTicks());
			write(entity, trimmed);
			return trimmed;
		}

		return state;
	}

	private static void write(LivingEntity entity, ManaState state) {
		entity.setAttached(GrandCraftAttachments.MANA, state);
	}
}
