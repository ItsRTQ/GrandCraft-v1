package com.hrtq.grandcraft.combat;

import com.hrtq.grandcraft.network.AttackLockoutPayload;
import com.hrtq.grandcraft.network.AttackMissPayload;
import com.hrtq.grandcraft.network.DodgePayload;
import com.hrtq.grandcraft.player.GrandCraftAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
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

		// A dodge that is running makes damage miss outright. ALLOW_DAMAGE is a veto,
		// which is exactly the shape i-frames want — nothing to reduce, only to refuse.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			CombatController controller = entity.getAttached(GrandCraftAttachments.COMBAT_CONTROLLER);

			if (controller == null || !controller.isDodgeInvulnerable()) {
				return true;
			}

			// A dodge evades attacks, not consequences. Anything that bypasses ordinary
			// invulnerability — the void, starvation, /kill — still lands, using
			// vanilla's own tag rather than a list of damage types that would rot.
			return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.isCreativePlayer();
		});

		registerMissPenalty();
		registerDodge();
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
