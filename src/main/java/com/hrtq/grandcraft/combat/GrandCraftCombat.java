package com.hrtq.grandcraft.combat;

import com.hrtq.grandcraft.network.AttackLockoutPayload;
import com.hrtq.grandcraft.network.AttackMissPayload;
import com.hrtq.grandcraft.network.DodgePayload;
import com.hrtq.grandcraft.network.GuardPayload;
import com.hrtq.grandcraft.player.GrandCraftAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Wires the combat system into the game.
 *
 * <p>Two of the three hooks need no mixin. {@link ServerLivingEntityEvents#AFTER_DAMAGE}
 * is server-only and fires after vanilla damage has been resolved, which is
 * exactly the point at which a hit becomes eligible to stagger; and
 * {@link AttackEntityCallback} is cancellable, which is all player attack gating
 * requires. Only mob melee phasing needs a mixin, because it has to intercept a
 * goal's internal attack call — see {@code MeleeAttackGoalMixin}.
 */
public final class GrandCraftCombat {
	/** A few flecks, not a shower — this fires on every blocked hit. */
	private static final int GUARD_PARTICLE_COUNT = 5;

	/**
	 * How loosely the flecks start out around the point of contact, in blocks. Tight,
	 * because they are thrown outward from it rather than scattered across it.
	 */
	private static final double GUARD_PARTICLE_SPREAD = 0.05;

	/**
	 * How hard the flecks are thrown outward, and it has to be read alongside what the
	 * particle does with it rather than as a speed.
	 *
	 * <p>{@code GlowParticle$WaxOffProvider} ignores the velocity it is handed and
	 * substitutes {@code speed * 0.01} vertically and {@code speed * 0.005} sideways,
	 * so an ordinary-looking figure here comes out as invisible drift. Twenty five is
	 * about an eighth of a block a tick sideways once that scaling is applied, which
	 * with the particle's own friction of 0.96 is a burst that flies clear of the
	 * impact and then settles.
	 *
	 * <p>Vertical travel ends up twice the horizontal, since only the sideways axes
	 * are halved. That suits a shower of sparks off a parried blow, so it is left
	 * alone rather than corrected for.
	 */
	private static final double GUARD_PARTICLE_SPEED = 25.0;

	/** How far from the defender's chest, towards the attacker, the burst sits. */
	private static final double GUARD_PARTICLE_REACH = 0.45;

	/** Where up the hitbox the chest is, as a fraction of its height. */
	private static final double CHEST_HEIGHT = 0.75;

	private GrandCraftCombat() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(
				(entity, source, baseDamageTaken, damageTaken, blocked) -> {
					// The Fabric jar is stripped of parameter names, so which float is
					// the pre-mitigation amount is positional inference only. Requiring
					// both to be positive is correct either way: a fully blocked or
					// absorbed hit leaves the final amount at zero, and a hit that
					// really landed has both above zero. The boolean is deliberately
					// unused for the same reason.
					if (baseDamageTaken <= 0.0F || damageTaken <= 0.0F) {
						return;
					}

					if (entity.isDeadOrDying() || !CombatProfiles.isCombatant(entity)) {
						return;
					}

					CombatController controller = controllerOf(entity);
					controller.applyStagger(entity);

					// A stagger locks a player out of attacking, so their indicator
					// needs to know just as much as it does after their own swing.
					notifyLockout(entity, controller);
				});

		// Combatants need a controller before their first hit, because knockback
		// resistance has to already be in place when knockback is resolved.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!(entity instanceof LivingEntity living)) {
				return;
			}

			CombatProfile profile = CombatProfiles.forEntity(living);

			if (profile == null) {
				return;
			}

			// Null means never rolled. A mob that has rolled before keeps what it
			// rolled, which is the whole point of persisting it — this fires once in
			// an entity's life, not on every load.
			boolean firstSpawn = profile.actor().usesRandomStats()
					&& living.getAttached(GrandCraftAttachments.ROLLED_STATS) == null;

			if (firstSpawn) {
				living.setAttached(GrandCraftAttachments.ROLLED_STATS,
						RolledStats.roll(profile, living.getRandom()));
			}

			// Applied now rather than on the next tick, so max health already reflects
			// the roll before the top-up below reads it.
			controllerOf(living).refreshModifiers(living);

			if (firstSpawn) {
				// A raised max health does not raise current health, so without this a
				// mob that rolled tough would spawn already wounded.
				living.setHealth(living.getMaxHealth());
			}
		});

		AttackEntityCallback.EVENT.register((player, level, hand, target, hitResult) -> {
			// Never cancel on the client. The client fires this from
			// MultiPlayerGameMode.attack BEFORE sending the interact packet, so a
			// non-PASS result there suppresses the packet and the server never
			// learns an attack happened at all.
			if (level.isClientSide()) {
				return InteractionResult.PASS;
			}

			CombatProfile profile = CombatProfiles.forEntity(player);

			if (profile == null) {
				return InteractionResult.PASS;
			}

			CombatController controller = controllerOf(player);

			// Swinging out of a raised guard lowers it rather than being refused, so a
			// blocked hit can be answered immediately. Before the gate below, because
			// GUARDING is not NEUTRAL and would otherwise fail it.
			controller.cancelGuardForAttack(player, profile);

			// Locked out by recovery, a stagger, or an empty stamina pool: the swing is
			// refused outright. Any non-PASS result cancels vanilla's Player.attack.
			if (!controller.canStartAttack(profile)) {
				return InteractionResult.FAIL;
			}

			// Phase 1 does not delay player damage — the client already played the
			// swing and crit visuals at click time and there is no animation layer
			// to hide a server-side startup behind. So vanilla deals the damage on
			// its own timing and the controller only books the recovery that
			// follows, which is what makes the lockout above meaningful.
			controller.enterRecoveryOnly(player, profile);
			notifyLockout(player, controller);
			return InteractionResult.PASS;
		});

		// Phase packets are sent on transitions, so a viewer who was not yet tracking
		// an actor when it began its wind-up would have missed the only announcement
		// and see the hit arrive out of nowhere. Catches them up instead.
		EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
			if (!(entity instanceof LivingEntity living)) {
				return;
			}

			// Nullable read, never controllerOf: this fires for every entity that
			// enters any player's view, and allocating a controller for each would
			// hand one to every cow on the server.
			CombatController controller = living.getAttached(GrandCraftAttachments.COMBAT_CONTROLLER);

			if (controller != null) {
				controller.syncPhaseTo(living, player);
			}
		});

		// Both defensive verbs refuse damage outright rather than reducing it, so both
		// fit ALLOW_DAMAGE — a veto with nothing to scale. A guard that absorbs
		// everything it covers is the same shape as a dodge that is not there to be
		// hit, and refusing here is what keeps the red hurt flash, the damage sound and
		// the knockback from firing on a hit that never landed. Vanilla only suppresses
		// those for a real BLOCKS_ATTACKS item, and ours is deliberately gone.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			CombatController controller = entity.getAttached(GrandCraftAttachments.COMBAT_CONTROLLER);

			if (controller == null) {
				return true;
			}

			// Neither verb evades consequences. Anything that bypasses ordinary
			// invulnerability — the void, starvation, /kill — still lands, using
			// vanilla's own tag rather than a list of damage types that would rot.
			boolean unavoidable = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
					|| source.isCreativePlayer();

			if (controller.isDodgeInvulnerable()) {
				return unavoidable;
			}

			if (!controller.isGuarding() || unavoidable) {
				return true;
			}

			CombatProfile profile = CombatProfiles.forEntity(entity);

			if (profile == null || !profile.usesBlock()) {
				return true;
			}

			// Outside the arc the guard simply is not in the way: full damage, and the
			// ordinary stagger that follows is what knocks it down.
			if (!CombatController.coversDirection(entity, profile, source)) {
				return true;
			}

			controller.absorbBlockedHit(entity, profile, amount);

			// Read after absorbing, because that is what may have broken the guard.
			playGuardSound(entity, controller.isGuarding()
					? SoundEvents.SHIELD_BLOCK
					: SoundEvents.SHIELD_BREAK);

			spawnGuardParticles(entity, source);

			// Silent while the guard holds — attackLockoutTicks reports nothing for a
			// held guard — and a real lockout once it has broken into a stagger.
			notifyLockout(entity, controller);
			return false;
		});

		registerMissPenalty();
		registerDodge();
		registerGuard();
	}

	/**
	 * Raises, renews and drops the player's guard.
	 *
	 * <p>The client reports only that a key is held. Whether that becomes a guard,
	 * what it costs and what it stops are all decided here, and the off-hand is read
	 * server side — so a modified client cannot claim a shield it is not carrying.
	 */
	private static void registerGuard() {
		ServerPlayNetworking.registerGlobalReceiver(GuardPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			CombatProfile profile = CombatProfiles.forEntity(player);

			if (profile == null || !profile.usesBlock()) {
				return;
			}

			CombatController controller = controllerOf(player);

			if (!payload.held()) {
				controller.releaseGuard(player, profile);
				return;
			}

			// The same message does double duty. The two calls are mutually exclusive by
			// construction — one only acts while a guard is up, the other only from
			// neutral — which is what lets a player who never let go get their guard
			// back on its own the moment a stagger or an empty pool stops refusing it.
			controller.refreshGuardHold();
			controller.beginGuard(player, profile);
		});
	}

	/**
	 * Plays the guard's own feedback.
	 *
	 * <p>Necessary rather than decorative: vanilla plays a block sound only when a
	 * real {@code BLOCKS_ATTACKS} item did the blocking, and refusing the damage means
	 * none of its usual hit feedback fires either. Without this a blocked hit would be
	 * completely silent, which reads as the hit having missed.
	 *
	 * <p>Pitch jitter matches vanilla's own for a shield block.
	 */
	private static void playGuardSound(LivingEntity entity, Holder<SoundEvent> sound) {
		entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
				sound, entity.getSoundSource(),
				1.0F, 0.8F + entity.getRandom().nextFloat() * 0.4F);
	}

	/**
	 * Scatters a few sparks off the guard where the blow landed on it.
	 *
	 * <p>The sound says something was stopped; this says <em>where</em>, which is the
	 * part that teaches. A guard has an arc, and a player who cannot see the point of
	 * contact has no way to learn where its edge is except by dying near it.
	 *
	 * <p>Placed between the two fighters at chest height rather than at the defender's
	 * feet, which is where the position of a {@code LivingEntity} actually is. Falls
	 * back to the defender's own chest when the damage has no position — rare, since
	 * the arc test already refuses those, but the guard costs nothing.
	 *
	 * <p>{@code WAX_OFF} is used for its look rather than its meaning: a short-lived
	 * scatter of pale flecks, which reads as a glancing impact and is already in every
	 * resource pack.
	 */
	private static void spawnGuardParticles(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}

		Vec3 chest = entity.position().add(0.0, entity.getBbHeight() * CHEST_HEIGHT, 0.0);
		Vec3 sourcePos = source.getSourcePosition();
		Vec3 at = chest;

		if (sourcePos != null) {
			Vec3 towards = new Vec3(sourcePos.x - chest.x, 0.0, sourcePos.z - chest.z);

			// Normalised only when there is a direction to take; a source standing
			// exactly on the defender leaves the burst on the chest.
			if (towards.lengthSqr() > 1.0E-6) {
				at = chest.add(towards.normalize().scale(GUARD_PARTICLE_REACH));
			}
		}

		// With a count above zero the three distances are random position offsets and
		// the last argument is the speed each fleck is thrown at, in a random
		// direction — which is what makes this a burst rather than a puff.
		level.sendParticles(ParticleTypes.WAX_OFF, at.x, at.y, at.z,
				GUARD_PARTICLE_COUNT,
				GUARD_PARTICLE_SPREAD, GUARD_PARTICLE_SPREAD, GUARD_PARTICLE_SPREAD,
				GUARD_PARTICLE_SPEED);
	}

	/**
	 * Starts a dodge on the player's request.
	 *
	 * <p>The client supplies only a direction; every other decision — whether the
	 * dodge is legal, its cost, its distance, and how long it protects — is made
	 * here. Silently ignored when refused, the same as a miss report: the player
	 * could not have dodged, so there is nothing to tell them beyond the absence of
	 * a roll.
	 */
	private static void registerDodge() {
		ServerPlayNetworking.registerGlobalReceiver(DodgePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			CombatProfile profile = CombatProfiles.forEntity(player);

			if (profile == null || !profile.usesDodge()) {
				return;
			}

			Vec3 direction = new Vec3(payload.x(), 0.0, payload.z());

			// A zero or non-finite direction would leave the actor rolling on the spot
			// or poison its velocity, so fall back to where it is facing.
			if (!isUsable(direction)) {
				direction = player.getLookAngle();
			}

			CombatController controller = controllerOf(player);

			if (controller.beginDodge(player, profile, direction)) {
				// A dodge locks out attacking for its whole length, and the attack
				// indicator already knows how to draw a lockout — so the crosshair
				// refills as the roll ends, for free.
				notifyLockout(player, controller);
			}
		});
	}

	private static boolean isUsable(Vec3 direction) {
		double lengthSqr = direction.x * direction.x + direction.z * direction.z;

		return Double.isFinite(lengthSqr) && lengthSqr > 1.0E-4;
	}

	/**
	 * A swing that hit nothing still commits the player, so a miss is punished the
	 * same way a hit is.
	 *
	 * <p>Silently ignored when the player is not free to attack: they could not have
	 * thrown that swing, so it must not extend a lockout they are already serving.
	 */
	private static void registerMissPenalty() {
		ServerPlayNetworking.registerGlobalReceiver(AttackMissPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			CombatProfile profile = CombatProfiles.forEntity(player);

			if (profile == null) {
				return;
			}

			CombatController controller = controllerOf(player);

			// Same as a connecting swing: committing to an attack lowers the guard.
			// Handled here too so a whiff cannot be a free way to swing from behind a
			// guard that stays up.
			controller.cancelGuardForAttack(player, profile);

			if (!controller.canStartAttack(profile)) {
				return;
			}

			controller.enterRecoveryOnly(player, profile);
			notifyLockout(player, controller);
		});
	}

	/**
	 * Tells a player's client how long it may not attack for, so the vanilla attack
	 * indicator can show the wait instead of claiming the swing is ready.
	 *
	 * <p>Only players have an indicator, so mobs are skipped rather than sent a
	 * packet nothing would read.
	 */
	private static void notifyLockout(LivingEntity entity, CombatController controller) {
		if (!(entity instanceof ServerPlayer player)) {
			return;
		}

		int ticks = controller.attackLockoutTicks();

		if (ticks > 0) {
			ServerPlayNetworking.send(player, new AttackLockoutPayload(ticks));
		}
	}

	/**
	 * The actor's controller, created on first use.
	 *
	 * <p>Call this only from paths that are actually starting combat, so entities
	 * that never fight never allocate one.
	 */
	public static CombatController controllerOf(LivingEntity entity) {
		return entity.getAttachedOrCreate(GrandCraftAttachments.COMBAT_CONTROLLER);
	}
}
