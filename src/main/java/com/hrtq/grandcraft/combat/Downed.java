package com.hrtq.grandcraft.combat;

import com.hrtq.grandcraft.mixin.EntityIdAccessor;
import com.hrtq.grandcraft.network.DownedPayload;
import com.hrtq.grandcraft.player.GrandCraftAttachments;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The rules of the downed state: who falls instead of dying, what ends it, and what
 * it costs.
 *
 * <p><strong>This is the file to edit when going down behaves wrongly.</strong>
 * {@code CombatController} holds the two clocks and the crawl modifier and never
 * learns what being downed means — the same split {@code skill/Acrobat} draws, and
 * for the same reason: the state machine is generic and this is the policy.
 *
 * <h2>The shape</h2>
 * <ul>
 *   <li><strong>A blow that would kill</strong> puts the actor prone with the
 *       configured bleed-out clock instead, at one health.</li>
 *   <li><strong>Nothing hurts them any more</strong> — a hit that lands takes time
 *       off the clock instead of health off the bar.</li>
 *   <li><strong>Three ways out</strong>: an ally holds a revive on them, they hold
 *       their own give-up key, or the clock reaches zero. The last two are the same
 *       ending reached by two different decisions.</li>
 * </ul>
 *
 * <h2>Why the death is re-run rather than simulated</h2>
 * When the state ends in a death, this does not call {@code die} or zero the health:
 * it re-applies the killing blow with {@code CombatController.markDying} set, and
 * {@link #allowDeath} lets that one through. Vanilla's death is a great deal more than a health value —
 * the death message naming who did it, the advancement, the drops, the statistics,
 * the totem check — and every shortcut past it is a list of those to reimplement.
 * The flag is what stops the second attempt being caught by the same hook that
 * caught the first, and it is per controller rather than global because two players
 * can bleed out on the same tick.
 *
 * <h2>What is deliberately still allowed</h2>
 * Being hurt by anything that bypasses invulnerability. The void, {@code /kill} and
 * a creative player still kill a downed actor outright, using vanilla's own tag
 * rather than a list of damage types that would rot — exactly the concession the
 * dodge and the guard already make in {@code GrandCraftCombat.ALLOW_DAMAGE}. A
 * downed player at the bottom of the world is not a puzzle worth having.
 */
public final class Downed {
	private Downed() {
	}

	/**
	 * Answers the death, and is the whole entry point to the state.
	 *
	 * @return true to let the actor die, false to have caught it — in which case the
	 *         actor is now prone and the caller must not run vanilla's death
	 */
	public static boolean allowDeath(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer player)) {
			return true;
		}

		CombatProfile profile = CombatProfiles.forEntity(player);

		if (profile == null || !profile.usesDowned()) {
			return true;
		}

		CombatController controller = GrandCraftCombat.controllerOf(player);

		// The second attempt at the same death: the state has already ended and this is
		// Downed itself asking for the blow to land. Cleared here rather than by the
		// caller so that it cannot survive a death that was refused for some other
		// reason further down.
		if (controller.consumeDyingFlag()) {
			return true;
		}

		// Already prone. Reached when something killed a downed actor outright rather
		// than through the clock — a source below is one — and standing them up to put
		// them down again would restart the timer they were about to run out of.
		if (controller.isDowned()) {
			return true;
		}

		if (isUnavoidable(source) || player.isCreative() || player.isSpectator()) {
			return true;
		}

		// BEFORE the state, and not optional. Cancelling a death does not restore any
		// health — the actor is left at zero and dies again on the next thing that
		// touches it, including the damage tick it is already inside. If going down
		// ever reads as "it did not work", this line is the first suspect.
		player.setHealth(1.0F);
		player.setDeltaMovement(Vec3.ZERO);
		player.hurtMarked = true;

		controller.beginDowned(player, profile.downed());
		controller.rememberKillingBlow(source);
		sync(player, controller);
		return false;
	}

	/**
	 * The clock, and the three ways off it. Called once per server tick from
	 * {@code LivingEntityMixin}, after the controller's own tick.
	 *
	 * <p>After rather than before, for {@code Acrobat.tick}'s reason: the controller
	 * has already spent this tick's bleed-out and already decayed an abandoned revive,
	 * so what is read here is this tick's truth rather than last tick's.
	 */
	public static void tick(LivingEntity entity, CombatController controller) {
		if (!(entity instanceof ServerPlayer player)) {
			return;
		}

		if (!controller.isDowned()) {
			return;
		}

		CombatProfile profile = CombatProfiles.forEntity(player);

		// The feature being switched off underneath someone lying on the floor, and the
		// only place it is caught. Standing them up is the kind answer: the alternative
		// is killing a player because an admin edited a config field.
		if (profile == null || !profile.usesDowned()) {
			stand(player, controller, 1.0F);
			return;
		}

		DownedSettings settings = profile.downed();

		if (controller.reviveTicks() >= settings.reviveTicks()) {
			stand(player, controller,
					(float) (player.getMaxHealth() * settings.reviveHealthFraction()));
			return;
		}

		if (controller.giveUpTicks() >= settings.giveUpHoldTicks()
				&& settings.giveUpHoldTicks() > 0) {
			finish(player, controller);
			return;
		}

		if (controller.bleedOutTicks() <= 0) {
			finish(player, controller);
			return;
		}

		// Throttled: the clock moves every tick and the client counts it down itself.
		// Every other send in this file is a state change and goes out at once.
		if (controller.downedSyncDue()) {
			sync(player, controller);
		}
	}

	/**
	 * Converts a hit on a downed actor into time off its clock, and makes it felt.
	 *
	 * <p>Called from the damage veto rather than after it, because the hit must never
	 * reach the health bar: a downed player sitting at one health would be killed by
	 * the next scratch, and the clock would mean nothing.
	 *
	 * <p><strong>The feedback has to be replayed by hand, and that is not optional.</strong>
	 * Vetoing damage takes vanilla's whole hit reaction with it — the red flash, the hurt
	 * sound, the direction the blow came from — because vanilla only fires those for a hit
	 * that landed. A downed player would then be losing two seconds of their life per
	 * swing with nothing whatsoever on screen to say so, which is indistinguishable from
	 * a mob that keeps missing. That is exactly what <em>"mobs can hardly hit the player
	 * when down"</em> (user, 2026-08-09) is describing, and it is the same trap
	 * {@code GrandCraftCombat} records for the guard: refusing damage refuses its
	 * feedback too, so a verb that refuses damage owes the player a replacement.
	 *
	 * <p>{@code broadcastDamageEvent} is vanilla's own path for it rather than a raw
	 * entity event, because it carries the source — so the flash and the sound are the
	 * ones the blow would have produced, and the hit comes from the right direction.
	 */
	public static void onDamageWhileDowned(LivingEntity entity, CombatController controller,
			DamageSource source, float amount) {
		CombatProfile profile = CombatProfiles.forEntity(entity);

		if (profile == null) {
			return;
		}

		controller.spendBleedOut(profile.downed(), amount);
		entity.level().broadcastDamageEvent(entity, source);
	}

	/**
	 * One tick of somebody's revive, if they are actually in a position to give it.
	 *
	 * <p>Every claim the client made is re-checked here — that the target is a real,
	 * downed player, that the reviver is close enough, and that the reviver is in a
	 * state to be doing anything at all. A modified client can ask; it cannot revive
	 * from across the map, or while lying on the floor itself.
	 *
	 * @return true when the progress was granted
	 */
	public static boolean requestRevive(ServerPlayer reviver, LivingEntity target) {
		if (!(target instanceof ServerPlayer downed) || reviver == downed) {
			return false;
		}

		CombatProfile profile = CombatProfiles.forEntity(downed);

		if (profile == null || !profile.usesDowned()) {
			return false;
		}

		CombatController reviverController =
				reviver.getAttached(GrandCraftAttachments.COMBAT_CONTROLLER);

		// A downed player cannot pick anyone else up, and neither can one mid-swing,
		// mid-roll or staggered. Nullable read: a player who has never fought has no
		// controller, and that player is perfectly able to revive.
		if (reviverController != null
				&& (reviverController.isDowned() || !reviverController.canActFreely())) {
			return false;
		}

		CombatController controller = downed.getAttached(GrandCraftAttachments.COMBAT_CONTROLLER);

		if (controller == null || !controller.isDowned()) {
			return false;
		}

		double reach = profile.downed().reviveReachBlocks();

		// Squared, and against the same reach the client tested, so the two agree at
		// the boundary rather than one of them cutting out a little sooner.
		if (reviver.distanceToSqr(downed) > reach * reach) {
			return false;
		}

		// Renews a claim rather than adding progress. The counting is per tick, in the
		// controller — see its note on why a hold measured in ticks cannot be counted in
		// packets.
		controller.renewRevive();
		return true;
	}

	/** The actor's own decision, arriving as a held key. */
	public static void setGivingUp(ServerPlayer player, boolean held) {
		CombatController controller = player.getAttached(GrandCraftAttachments.COMBAT_CONTROLLER);

		if (controller == null || !controller.isDowned()) {
			return;
		}

		controller.setGivingUp(held);
	}

	/**
	 * Kills a player who is logging out while down.
	 *
	 * <p>Otherwise it is the one free escape from the state: the controller is
	 * transient, so a player who quit and came back would be standing up at one health
	 * with the clock forgotten. Most of the cost of going down is the minute you spend
	 * unable to play, and a rule you can leave by pressing Escape is not a cost.
	 */
	public static void onDisconnect(ServerPlayer player) {
		CombatController controller = player.getAttached(GrandCraftAttachments.COMBAT_CONTROLLER);

		if (controller == null || !controller.isDowned()) {
			return;
		}

		// A real death when the entity is still live enough to have one — drops, death
		// message, statistics, all of it. Whether it is depends on where Fabric fires
		// DISCONNECT relative to PlayerList.remove, which is not something the bytecode
		// settles, so this asks the entity rather than assuming an answer.
		if (!player.isRemoved() && player.level() instanceof ServerLevel) {
			finish(player, controller);
			return;
		}

		// Too late for a proper death. Zeroing the health is the fallback because it is
		// the one thing that has to be true: the state is transient, so a player who came
		// back alive would be standing up with the clock forgotten, which is a free escape
		// from the whole feature. They lose the drops rather than the death.
		controller.clearDowned(player);
		player.setHealth(0.0F);
	}

	/**
	 * Whether this actor is prone — answered correctly on <strong>either side</strong>.
	 *
	 * <p>The controller is a transient, unsynced attachment, so on a client it is always
	 * absent and a naive read answers "no" for everyone including yourself. That is fine
	 * for a rule the server enforces alone, and wrong for anything the two sides have to
	 * agree about — the hitbox above all, since a client that thinks a downed player is
	 * standing collides with a box the server does not have.
	 *
	 * <p>So the client is asked through {@link CombatPhaseView}, which is the seam that
	 * already exists for exactly this: common code needing the phase data that only a
	 * client has. On a server it stays {@code NEUTRAL_ONLY} and is never consulted.
	 */
	public static boolean isDowned(LivingEntity entity) {
		if (entity.level().isClientSide()) {
			// NEVER entity.getId() here. This is reached from the hitbox override, which
			// a constructor can reach through refreshDimensions — and getId() throws
			// outright until the id has been assigned, which happens after the
			// constructor returns. It cost a dead connection the first time (2026-08-09):
			// a salmon spawned, its constructor asked for its own dimensions, and the
			// client disconnected with a protocol error. See EntityIdAccessor.
			int id = ((EntityIdAccessor) entity).grandcraft$rawId();

			// Still being built, so it is nobody's idea of downed and vanilla will ask
			// again once it is in the world.
			if (id == 0) {
				return false;
			}

			return CombatPhaseView.get().stateOf(id) == CombatState.DOWNED;
		}

		CombatController controller = entity.getAttached(GrandCraftAttachments.COMBAT_CONTROLLER);

		return controller != null && controller.isDowned();
	}

	/** Back on their feet, with whatever health the way out of it grants. */
	private static void stand(ServerPlayer player, CombatController controller, float health) {
		controller.clearDowned(player);

		// A floor of one, not of zero: standing a player up dead would put them
		// straight back through the death path they were caught on.
		player.setHealth(Math.max(1.0F, health));
		sync(player, controller);
	}

	/**
	 * The death, at last — by the clock or by the player's own hand.
	 *
	 * <p>The state is cleared <em>first</em>, so that the blow about to be dealt sees
	 * an ordinary standing player. Leaving it set would have {@link #allowDeath} take
	 * the "already prone" branch and the dying flag would go unread.
	 */
	private static void finish(ServerPlayer player, CombatController controller) {
		DamageSource source = controller.killingBlow();

		controller.clearDowned(player);
		controller.markDying();
		sync(player, controller);

		// The blow that downed them, re-applied, so the death message still names what
		// killed them a minute ago. Falls back to vanilla's own generic kill when the
		// original source did not survive — an entity that has since been unloaded.
		if (player.level() instanceof ServerLevel level) {
			player.hurtServer(level,
					source != null ? source : player.damageSources().genericKill(),
					Float.MAX_VALUE);
		}

		// Belt and braces. If anything at all refused that blow, the flag would still
		// be set and the next real death would be let through unexamined — so it is
		// cleared here rather than left for the next hook to find.
		controller.consumeDyingFlag();
	}

	/**
	 * Tells the owner's client about their own clock.
	 *
	 * <p>Owner only, unlike the phase packet. The pose is information for everyone —
	 * an ally has to be able to see who is down — but the numbers are the player's
	 * own, and a HUD is the only thing that draws them.
	 */
	private static void sync(ServerPlayer player, CombatController controller) {
		CombatProfile profile = CombatProfiles.forEntity(player);

		// The two totals the HUD needs to draw its bars. Taken from the live settings on
		// every send rather than banked when the state began, so an admin who retunes the
		// revive length mid-fight does not leave a bar measured against the old one.
		DownedSettings settings = profile == null ? null : profile.downed();

		ServerPlayNetworking.send(player, new DownedPayload(
				controller.isDowned(),
				controller.bleedOutTicks(),
				controller.bleedOutTotalTicks(),
				controller.reviveTicks(),
				settings == null ? 0 : settings.reviveTicks(),
				controller.giveUpTicks(),
				settings == null ? 0 : settings.giveUpHoldTicks()));
	}

	/**
	 * Whether this is the kind of damage nothing evades.
	 *
	 * <p>Vanilla's own tag rather than a list of damage types, for the reason the guard
	 * gives at the same seam: a list would rot the first time a damage type was added.
	 */
	private static boolean isUnavoidable(DamageSource source) {
		return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
				|| source.isCreativePlayer();
	}
}
