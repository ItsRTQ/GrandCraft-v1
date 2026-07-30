package com.hrtq.grandcraft.combat;

import com.hrtq.grandcraft.GrandCraft;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Owns one actor's combat state, phase timers and stagger sequence.
 *
 * <p>Server-authoritative: nothing here is driven by the client. Deliberately
 * holds no reference back to its entity, because the attachment initializer is a
 * no-argument supplier — every method that needs the entity is handed it.
 *
 * <h2>Stagger and attack commitment</h2>
 * The slowdown and attack lockout live on {@link #staggerTicks}, a timer that
 * runs independently of {@link #state}. {@link CombatState#STAGGERED} is entered
 * as a state only from NEUTRAL or ATTACK_STARTUP. That single split gives the
 * required priorities with no special cases:
 *
 * <ul>
 *   <li>NEUTRAL: becomes STAGGERED.</li>
 *   <li>ATTACK_STARTUP: the attack is cancelled and the actor becomes STAGGERED,
 *       so hitting an opponent before their active frames is rewarded.</li>
 *   <li>ATTACK_ACTIVE: state is untouched, the attack stays committed and
 *       completes. Rapid hits therefore cannot perpetually cancel attacks.</li>
 *   <li>ATTACK_RECOVERY: state is untouched, so recovery commitment can be
 *       neither shortened nor escaped.</li>
 * </ul>
 *
 * In the latter two cases the slowdown and lockout still apply.
 */
public final class CombatController {
	private static final Identifier STAGGER_SPEED_ID = GrandCraft.id("stagger_speed");
	private static final Identifier STAGGER_JUMP_ID = GrandCraft.id("stagger_jump");
	private static final Identifier KNOCKBACK_ID = GrandCraft.id("combat_knockback");

	/** Sentinel meaning "no knockback modifier applied yet", distinct from any real value. */
	private static final double KNOCKBACK_UNSET = -1.0;

	private final StaggerTracker stagger = new StaggerTracker();

	private CombatState state = CombatState.NEUTRAL;

	/** Ticks left in the current timed state. */
	private int stateTicks;

	/** The swing in flight, or null when idle. */
	private AttackProfile attack;

	/** One swing deals damage to a target once, however long the active window is. */
	private boolean activeHitConsumed;

	/** Slowdown and attack lockout, independent of {@link #state}. */
	private int staggerTicks;

	/** Knockback resistance currently applied, so the attribute is only touched on change. */
	private double appliedKnockbackResistance = KNOCKBACK_UNSET;

	public CombatState state() {
		return this.state;
	}

	/** Advances all combat timers. Server side only, once per entity tick. */
	public void tick(LivingEntity entity) {
		syncKnockbackResistance(entity);
		this.stagger.tick();

		if (this.staggerTicks > 0) {
			this.staggerTicks--;

			if (this.staggerTicks == 0) {
				clearStaggerModifiers(entity);
			}
		}

		if (this.stateTicks > 0) {
			this.stateTicks--;

			if (this.stateTicks == 0) {
				advanceState();
			}
		}
	}

	/**
	 * Begins an attack if the actor is free to act.
	 *
	 * @return false when the actor is mid-swing or staggered, in which case the
	 *         caller must suppress the attack.
	 */
	public boolean beginAttack(AttackProfile profile) {
		if (!canStartAttack()) {
			return false;
		}

		this.attack = profile;
		this.activeHitConsumed = false;
		enter(CombatState.ATTACK_STARTUP, profile.startupTicks());
		return true;
	}

	/** An actor may only start an attack from neutral, and never while staggered. */
	public boolean canStartAttack() {
		return this.state == CombatState.NEUTRAL && this.staggerTicks == 0;
	}

	/** True only during the active window, and only until this swing's hit is spent. */
	public boolean canDealDamage() {
		return this.state == CombatState.ATTACK_ACTIVE && !this.activeHitConsumed;
	}

	/**
	 * Marks this swing's damage as spent. The swing keeps running to recovery,
	 * so a miss still costs recovery.
	 */
	public void consumeActiveHit() {
		this.activeHitConsumed = true;
	}

	/**
	 * Puts the actor straight into recovery without a startup or active phase.
	 * Used for the player, whose damage still lands on vanilla timing in Phase 1.
	 */
	public void enterRecoveryOnly(AttackProfile profile) {
		this.attack = profile;
		this.activeHitConsumed = true;
		enter(CombatState.ATTACK_RECOVERY, profile.recoveryTicks());
	}

	/**
	 * Applies the hit reaction for one qualifying hit.
	 *
	 * <p>A suppressed stagger is not a suppressed hit: damage, sound, particles
	 * and knockback have already happened by the time this is called.
	 */
	public void applyStagger(LivingEntity entity) {
		int level = this.stagger.registerHit();

		if (level == 0) {
			return;
		}

		this.staggerTicks = CombatTuning.staggerTicks(level);
		applyStaggerModifiers(entity, CombatTuning.staggerSpeedPenalty(level));

		if (this.state == CombatState.NEUTRAL || this.state == CombatState.ATTACK_STARTUP) {
			// Cancels a wind-up; a committed attack is left alone. See class docs.
			this.attack = null;
			this.activeHitConsumed = false;
			enter(CombatState.STAGGERED, this.staggerTicks);
		}
	}

	private void advanceState() {
		switch (this.state) {
			case ATTACK_STARTUP -> enter(CombatState.ATTACK_ACTIVE, this.attack.activeTicks());
			case ATTACK_ACTIVE -> enter(CombatState.ATTACK_RECOVERY, this.attack.recoveryTicks());
			case ATTACK_RECOVERY, STAGGERED -> returnToNeutral();
			case NEUTRAL -> {
			}
		}
	}

	private void enter(CombatState next, int ticks) {
		this.state = next;
		this.stateTicks = Math.max(ticks, 0);

		// A zero-length phase falls straight through rather than sticking.
		// Terminates because the states that lead to returnToNeutral do not re-enter.
		if (this.stateTicks == 0) {
			advanceState();
		}
	}

	private void returnToNeutral() {
		this.state = CombatState.NEUTRAL;
		this.stateTicks = 0;
		this.attack = null;
		this.activeHitConsumed = false;
	}

	private void applyStaggerModifiers(LivingEntity entity, double speedPenalty) {
		scale(entity, Attributes.MOVEMENT_SPEED, STAGGER_SPEED_ID, speedPenalty);

		// Movement speed governs ground acceleration, so without this a staggered
		// actor can jump and carry its pre-stagger momentum straight through the
		// slowdown. Cutting jump strength shortens that airtime.
		scale(entity, Attributes.JUMP_STRENGTH, STAGGER_JUMP_ID, CombatTuning.staggerJumpPenalty());
	}

	private void clearStaggerModifiers(LivingEntity entity) {
		remove(entity, Attributes.MOVEMENT_SPEED, STAGGER_SPEED_ID);
		remove(entity, Attributes.JUMP_STRENGTH, STAGGER_JUMP_ID);
	}

	/**
	 * Keeps the combatant's knockback resistance matching the current settings.
	 *
	 * <p>Held permanently rather than applied on hit, because knockback is resolved
	 * during the damage event — by the time a stagger is applied the target is
	 * already moving. Only touches the attribute when the configured value actually
	 * changes, so the per-tick cost is a double comparison.
	 */
	private void syncKnockbackResistance(LivingEntity entity) {
		double wanted = CombatTuning.knockbackResistance();

		if (wanted == this.appliedKnockbackResistance) {
			return;
		}

		AttributeInstance resistance = entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE);

		if (resistance == null) {
			return;
		}

		// Base knockback resistance is 0 and vanilla scales knockback by
		// (1 - resistance), so this adds a flat value rather than multiplying.
		resistance.addOrUpdateTransientModifier(new AttributeModifier(
				KNOCKBACK_ID, wanted, AttributeModifier.Operation.ADD_VALUE));
		this.appliedKnockbackResistance = wanted;
	}

	private static void scale(LivingEntity entity, Holder<Attribute> attribute,
			Identifier id, double penalty) {
		AttributeInstance instance = entity.getAttribute(attribute);

		if (instance != null) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(
					id, penalty, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private static void remove(LivingEntity entity, Holder<Attribute> attribute, Identifier id) {
		AttributeInstance instance = entity.getAttribute(attribute);

		if (instance != null) {
			instance.removeModifier(id);
		}
	}
}
