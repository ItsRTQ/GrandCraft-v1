package com.hrtq.grandcraft.stats;

/**
 * The mana pool's size and how fast it comes back.
 *
 * <p>Derived from {@link StatSettings} rather than stored on its own, and
 * deliberately not part of {@code ActorSettings}: mana is a character resource that
 * belongs to the stat system, and filing it under the per-actor combat config would
 * say it is a combat verb some mobs opt into, which it is not.
 *
 * <p>Nothing spends mana yet, so the regen values describe a pool that is always
 * full. They exist now so the magic layer inherits a working resource rather than
 * starting from a number on a screen.
 */
public record ManaSettings(int maxMana, int regenPerSecond, int regenDelayTicks) {

	/**
	 * Whether mana applies at all. A pool of zero is the config-level off switch, the
	 * same convention {@code StaminaSettings.enabled} uses.
	 */
	public boolean enabled() {
		return this.maxMana > 0;
	}

	/** Rates are configured per second and spent per tick. */
	public float regenPerTick() {
		return this.regenPerSecond / (float) TICKS_PER_SECOND;
	}

	private static final int TICKS_PER_SECOND = 20;
}
