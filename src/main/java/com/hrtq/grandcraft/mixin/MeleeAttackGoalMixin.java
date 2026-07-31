package com.hrtq.grandcraft.mixin;

import com.hrtq.grandcraft.combat.CombatController;
import com.hrtq.grandcraft.combat.CombatProfile;
import com.hrtq.grandcraft.combat.CombatProfiles;
import com.hrtq.grandcraft.combat.GrandCraftCombat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives every {@link MeleeAttackGoal} user a startup, active and recovery phase
 * instead of vanilla's instant hit.
 *
 * <p>One injection covers the whole melee family. {@code ZombieAttackGoal}
 * overrides only start/stop/tick, so it inherits this hook, and any other
 * subclass does too — opting a new mob in is a {@link CombatProfiles} table entry,
 * not new code here.
 *
 * <p>Vanilla's method is:
 * <pre>
 * if (canPerformAttack(target)) {
 *     resetAttackCooldown();
 *     mob.swing(MAIN_HAND);
 *     mob.doHurtTarget(getServerLevel(mob), target);
 * }
 * </pre>
 * so letting it run unchanged during the active window keeps vanilla's own range,
 * line-of-sight, cooldown and damage handling. Nothing about damage is
 * reimplemented; this hook only decides <em>when</em> vanilla is allowed to run.
 *
 * <p>Note that cancelling never resets the attack cooldown, because
 * {@code resetAttackCooldown} lives inside the body being skipped. That is what
 * keeps {@code isTimeToAttack()} true across the whole wind-up, so the swing that
 * was committed to in startup can still land when the active window opens.
 *
 * <p>A known cosmetic consequence of that: {@code ZombieAttackGoal} raises a
 * zombie's arms while {@code getTicksUntilNextAttack() < attackInterval / 2}, and
 * an unreset cooldown holds that at zero, so an engaged zombie keeps its arms up
 * almost continuously instead of the vanilla pulse.
 */
@Mixin(MeleeAttackGoal.class)
public abstract class MeleeAttackGoalMixin {
	@Shadow
	@Final
	protected PathfinderMob mob;

	@Shadow
	private int ticksUntilNextAttack;

	/** Vanilla's own gate: time to attack, within melee range, and has line of sight. */
	@Shadow
	protected abstract boolean canPerformAttack(LivingEntity target);

	@Inject(method = "checkAndPerformAttack", at = @At("HEAD"), cancellable = true)
	private void grandcraft$phaseMelee(LivingEntity target, CallbackInfo info) {
		// Goals only run server side, but combat state is server-authoritative and
		// this keeps that contract explicit rather than assumed.
		if (this.mob.level().isClientSide()) {
			return;
		}

		CombatProfile profile = CombatProfiles.forEntity(this.mob);

		// No profile means this mob is not part of GrandCraft combat. The flag means
		// it is, but its melee is not ours to phase — an actor can opt into stagger
		// and rolled stats while leaving its attacks on vanilla timing, which is how
		// a mob whose real attack runs through the brain or a ranged goal takes part
		// without this hook half-governing the melee goal it also happens to own.
		// Either way, leave vanilla untouched.
		if (profile == null || !profile.actor().usesMeleeGoal()) {
			return;
		}

		CombatController controller = GrandCraftCombat.controllerOf(this.mob);

		if (controller.canDealDamage()) {
			// The active window is open. Only spend this swing's single hit if
			// vanilla is actually going to connect, so a target that steps back in
			// before the window closes can still be hit.
			if (this.canPerformAttack(target)) {
				controller.consumeActiveHit();
				return;
			}

			info.cancel();
			return;
		}

		if (controller.canStartAttack(profile) && this.canPerformAttack(target)) {
			controller.beginAttack(this.mob, profile);

			// Deliberately no swing here. An earlier version telegraphed the wind-up
			// with one, but vanilla restarts a swing whenever the current one is past
			// half its ~6 tick duration, so an 8 tick startup produced two separate
			// arm swings with damage on the second — the first read as a phantom
			// attack. Vanilla's own convention is that the swing *starts* on impact.
			// The wind-up is now drawn by the animation layer instead, off the phase
			// packet beginAttack just sent — see HumanoidCombatPose.
		}

		// Startup, recovery, stagger lockout, or a spent hit: no damage this tick.
		info.cancel();
	}

	/**
	 * Hands cadence entirely to the phase timers.
	 *
	 * <p>Vanilla hardcodes a 20 tick cooldown here, measured from the moment damage
	 * lands, and a phased mob then spends its startup before the next hit — so the
	 * real cycle was {@code 20 + startup} and every startup change silently moved the
	 * mob's damage output. Zeroing the cooldown makes
	 * {@code startup + active + recovery} the whole story, so the two exposed sliders
	 * mean exactly what they look like they mean and nothing tunes itself behind
	 * them.
	 */
	@Inject(method = "resetAttackCooldown", at = @At("TAIL"))
	private void grandcraft$phasesOwnCadence(CallbackInfo info) {
		if (this.mob.level().isClientSide()) {
			return;
		}

		CombatProfile profile = CombatProfiles.forEntity(this.mob);

		if (profile == null || !profile.actor().usesMeleeGoal()) {
			return;
		}

		this.ticksUntilNextAttack = 0;
	}
}
