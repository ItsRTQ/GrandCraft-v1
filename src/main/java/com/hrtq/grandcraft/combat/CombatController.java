package com.hrtq.grandcraft.combat;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.network.CombatPhasePayload;
import com.hrtq.grandcraft.network.StaminaPayload;
import com.hrtq.grandcraft.player.GrandCraftAttachments;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

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
 *
 * <h2>The guard, and why it is held on a lease</h2>
 * Every other phase knows its own length when it begins, and the whole animation
 * pipeline is built on that. A guard does not: it lasts until the actor lets go.
 * Rather than teach the payload, the client store and the pose about an indefinite
 * phase, {@link CombatState#GUARDING} is granted in fixed leases of
 * {@link CombatConstants#GUARD_LEASE_TICKS} that renew themselves in
 * {@link #advanceState} while {@link #guardHeld} is still set. The client re-asserts
 * the hold periodically and {@link #guardHoldTicks} forgets it if that stops
 * arriving, so a release packet that never lands puts the guard down by itself
 * instead of leaving it standing forever.
 */
public final class CombatController {
	private static final Identifier STAGGER_SPEED_ID = GrandCraft.id("stagger_speed");
	private static final Identifier STAGGER_JUMP_ID = GrandCraft.id("stagger_jump");
	private static final Identifier KNOCKBACK_ID = GrandCraft.id("combat_knockback");
	private static final Identifier SPEED_ID = GrandCraft.id("combat_speed");
	private static final Identifier DAMAGE_ID = GrandCraft.id("combat_damage");
	private static final Identifier HEALTH_ID = GrandCraft.id("combat_health");
	private static final Identifier DEFENCE_ID = GrandCraft.id("combat_defence");
	private static final Identifier GUARD_SPEED_ID = GrandCraft.id("guard_speed");

	private final StaggerTracker stagger = new StaggerTracker();

	private final StaminaPool stamina = new StaminaPool();

	private CombatState state = CombatState.NEUTRAL;

	/** Ticks left in the current timed state. */
	private int stateTicks;

	/**
	 * How long the current state was when it began.
	 *
	 * <p>Only the animation layer needs this: {@link #stateTicks} counts down, and a
	 * client that is told nothing else cannot tell a wind-up two ticks from its end
	 * apart from one that was only ever two ticks long. Kept alongside rather than
	 * derived from {@link #attack}, because STAGGERED has no attack to derive from.
	 */
	private int phaseTotalTicks;

	/**
	 * Whether the client has been told about a phase that has not yet been retracted.
	 * See {@link #syncPhase} — it is what makes the return to neutral conditional on
	 * something having been announced rather than on neutral being animated.
	 */
	private boolean syncedPhaseActive;

	/** The swing in flight, or null when idle. */
	private AttackProfile attack;

	/** One swing deals damage to a target once, however long the active window is. */
	private boolean activeHitConsumed;

	/** Slowdown and attack lockout, independent of {@link #state}. */
	private int staggerTicks;

	/** Whether the actor is still asking to keep its guard up. */
	private boolean guardHeld;

	/**
	 * Ticks left before an un-renewed hold is forgotten. The client re-asserts every
	 * {@link CombatConstants#GUARD_KEEPALIVE_TICKS}, so this only ever runs out when
	 * the client has stopped talking — a disconnect, or a release that was lost.
	 */
	private int guardHoldTicks;

	/** Ticks before another routine stamina packet may be sent. */
	private int staminaSyncDelay;

	/**
	 * The pool as the owner's client last heard it. Starts at a value no real pool
	 * can hold, so the first tick always sends and no join hook is needed.
	 */
	private float syncedStamina = -1.0F;

	private boolean syncedExhausted;

	/**
	 * The profile whose permanent modifiers are currently on the entity, so the
	 * attributes are only touched when tuning actually changes.
	 *
	 * <p>Compared by identity: {@link CombatProfiles} rebuilds its profiles wholesale
	 * when settings are swapped, so a new object is exactly the signal to re-apply.
	 */
	private CombatProfile appliedProfile;

	/** The rolled stats currently expressed as modifiers, or null before the first apply. */
	private RolledStats appliedStats;

	public CombatState state() {
		return this.state;
	}

	/** Advances all combat timers. Server side only, once per entity tick. */
	public void tick(LivingEntity entity) {
		CombatProfile profile = CombatProfiles.forEntity(entity);

		if (profile == null) {
			// Cannot normally happen: an actor's identity comes from its entity
			// class, which never changes. Clearing rather than returning means a
			// controller that somehow outlived its profile cannot leave a slowdown
			// applied forever.
			clearStaggerModifiers(entity);
			remove(entity, Attributes.MOVEMENT_SPEED, GUARD_SPEED_ID);
			return;
		}

		syncPermanentModifiers(entity, profile);
		this.stagger.tick(profile.stagger().resetTicks());

		// Skipped entirely for an actor without stamina. Every stamina gate short
		// circuits on the same usesStamina() check, so its pool is never read either.
		if (profile.usesStamina()) {
			this.stamina.tick(profile.stamina());
			drainSprint(entity, profile);
			drainGuard(entity, profile);
			syncStamina(entity, profile);
		}

		// Runs whether or not the actor pays stamina: this is the dead man's switch on
		// the hold, not a cost. Counted down before the state timer so a hold that
		// lapsed this tick is already forgotten when the lease asks about it.
		if (this.guardHeld && --this.guardHoldTicks <= 0) {
			this.guardHeld = false;
		}

		// A guard needs something to guard with, and what it has can leave mid-block —
		// broken by the wear an absorbed hit charges, or simply swapped out of the
		// hand. Re-checked every tick rather than only at the raise for that reason.
		if ((this.state == CombatState.GUARD_RAISE || this.state == CombatState.GUARDING)
				&& !hasGuardImplement(entity)) {
			releaseGuard(entity, profile);
		}

		if (this.staggerTicks > 0) {
			this.staggerTicks--;

			if (this.staggerTicks == 0) {
				clearStaggerModifiers(entity);
			}
		}

		if (this.stateTicks > 0) {
			this.stateTicks--;

			if (this.stateTicks == 0) {
				advanceState(entity);
			}
		}
	}

	public StaminaPool stamina() {
		return this.stamina;
	}

	/**
	 * Begins an attack if the actor is free to act.
	 *
	 * @return false when the actor is mid-swing, staggered or out of stamina, in
	 *         which case the caller must suppress the attack.
	 */
	public boolean beginAttack(LivingEntity entity, CombatProfile profile) {
		if (!canStartAttack(profile)) {
			return false;
		}

		spendAttackCost(profile);
		this.attack = profile.melee();
		this.activeHitConsumed = false;
		enter(entity, CombatState.ATTACK_STARTUP, this.attack.startupTicks());
		return true;
	}

	/**
	 * An actor may only start an attack from neutral, never while staggered, and
	 * never without the stamina to pay for it.
	 *
	 * <p>Takes the profile rather than reading a cached one so the stamina gate
	 * cannot be bypassed by calling a no-argument overload — every caller already has
	 * the profile in hand.
	 */
	public boolean canStartAttack(CombatProfile profile) {
		if (this.state != CombatState.NEUTRAL || this.staggerTicks > 0) {
			return false;
		}

		return !profile.usesStamina()
				|| this.stamina.has(profile.stamina(), profile.stamina().attackCost());
	}

	/**
	 * Ticks until the phase and stagger timers stop blocking an attack, or 0 if they
	 * already do not. Both gates have to clear, so this is whichever runs longer.
	 * Used to tell a player's client how long to show its attack indicator unfilled.
	 *
	 * <p>Deliberately excludes stamina, even though {@link #canStartAttack} includes
	 * it. A stamina lockout has no fixed duration — it ends when the pool refills —
	 * and the stamina bar already shows exactly that. Feeding it into the attack
	 * indicator would state a countdown that is not real.
	 */
	public int attackLockoutTicks() {
		// GUARDING is excluded alongside NEUTRAL. Its remaining ticks say when the
		// current lease runs out, not when the actor is free, so reporting them would
		// make the indicator saw back and forth for as long as the guard is held. A
		// guard has no countdown to show for the same reason stamina does not: it ends
		// when the player lets go.
		int phase = this.state == CombatState.NEUTRAL || this.state == CombatState.GUARDING
				? 0
				: this.stateTicks;

		return Math.max(this.staggerTicks, phase);
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
	 *
	 * <p>Charges the attack's stamina whether the swing connected or missed. Callers
	 * are expected to have cleared {@link #canStartAttack} first, so the cost is
	 * spent rather than merely attempted.
	 */
	public void enterRecoveryOnly(LivingEntity entity, CombatProfile profile) {
		spendAttackCost(profile);
		this.attack = profile.melee();
		this.activeHitConsumed = true;
		enter(entity, CombatState.ATTACK_RECOVERY, this.attack.recoveryTicks());
	}

	/**
	 * Whether a dodge may start right now.
	 *
	 * <p>From neutral, so a dodge cannot be used to cancel the recovery of an attack
	 * or another dodge. That is the point of committing: the cost of a swing is that
	 * you are still in it.
	 *
	 * <p>A raised guard is the one exception. Rolling out from behind it is allowed
	 * because the alternative makes the guard a trap rather than an option — having
	 * to drop it and sit through the tail before a dodge could even begin would mean
	 * that committing to defence commits you to <em>that</em> defence. Being able to
	 * switch between the two verbs under pressure is most of what makes holding one
	 * of them interesting.
	 */
	public boolean canStartDodge(LivingEntity entity, CombatProfile profile) {
		if (!profile.usesDodge()) {
			return false;
		}

		if ((this.state != CombatState.NEUTRAL && this.state != CombatState.GUARDING)
				|| this.staggerTicks > 0) {
			return false;
		}

		// A step drives off the ground. Allowing one in mid-air would turn the dodge
		// into an air-dash — free horizontal distance out of a jump, and a second
		// chance to escape an attack you already committed to jumping over. It would
		// also make the lean read as nothing, since there is no push off to animate.
		//
		// Deliberately covers swimming and climbing too, both of which report not on
		// the ground: neither is a stance you can drive a step out of.
		if (!entity.onGround()) {
			return false;
		}

		return !profile.usesStamina()
				|| this.stamina.has(profile.stamina(), profile.dodge().cost());
	}

	/**
	 * Starts a dodge in the given direction and launches the actor along it.
	 *
	 * @param direction a horizontal unit vector; callers pass the actor's intended
	 *                  travel direction, which for a player is their movement input
	 * @return false when the dodge was refused, in which case nothing was spent
	 */
	public boolean beginDodge(LivingEntity entity, CombatProfile profile, Vec3 direction) {
		if (!canStartDodge(entity, profile)) {
			return false;
		}

		// Rolling out of a guard skips its tail entirely. Charging the drop before the
		// roll could start would defeat the point of allowing it at all, and the roll's
		// own recovery is already the price of the exchange.
		dropGuardHold();

		spendDodgeCost(profile);
		launch(entity, profile, direction);
		enter(entity, CombatState.DODGE_ACTIVE, profile.dodge().invulnerableTicks());
		return true;
	}

	/**
	 * Whether damage should pass straight through this actor.
	 *
	 * <p>True only for the front half of a dodge. The recovery half deliberately is
	 * not covered — that tail is the entire cost of the verb.
	 */
	public boolean isDodgeInvulnerable() {
		return this.state == CombatState.DODGE_ACTIVE;
	}

	/**
	 * Whether a guard may start right now.
	 *
	 * <p>Nothing is charged to raise one, so there is no stamina cost to clear — but
	 * an exhausted actor is refused anyway. A guard it could not hold for a single
	 * tick and that would shatter on the first hit is worse than no guard at all: it
	 * would read as the verb being broken rather than as the pool being empty.
	 */
	public boolean canStartGuard(LivingEntity entity, CombatProfile profile) {
		if (!profile.usesBlock() || !hasGuardImplement(entity)) {
			return false;
		}

		if (this.state != CombatState.NEUTRAL || this.staggerTicks > 0) {
			return false;
		}

		return !profile.usesStamina() || !this.stamina.exhausted();
	}

	/**
	 * Whether the actor is carrying something it could guard with: an off-hand shield,
	 * or a weapon in its main hand.
	 *
	 * <p>Checked here rather than only on the client, because the client is the one
	 * asking. Neither test names an item type: a shield is whatever carries vanilla's
	 * own blocking component, and a weapon is whatever a datapack has put in
	 * {@link GrandCraftTags#GUARD_IMPLEMENTS}.
	 */
	public static boolean hasGuardImplement(LivingEntity entity) {
		return guardHand(entity) != null;
	}

	/**
	 * Which hand is doing the blocking, or null when nothing can.
	 *
	 * <p>A single answer that everything else derives from — whether a guard may start,
	 * which item wears out, and whether the shield discount applies — so those three
	 * can never disagree about what is being blocked with.
	 *
	 * <p>A shield wins over a weapon wherever it is, and the off-hand wins when there
	 * are two, which is the usual way of carrying one. Checking the main hand for a
	 * shield matters more than it looks: a shield held in the main hand is not in the
	 * weapon tag and has no business being excluded, and leaving it out meant a player
	 * holding one had no guard at all.
	 */
	public static InteractionHand guardHand(LivingEntity entity) {
		return guardHand(entity.getMainHandItem(), entity.getOffhandItem());
	}

	/**
	 * The same choice made from two stacks alone.
	 *
	 * <p>Split out so the renderer can reach the identical answer: it draws other
	 * players from a render state rather than an entity, and the arm it poses has to
	 * be the arm the server is actually blocking with. One rule, two callers, no way
	 * for the picture and the mechanics to disagree.
	 */
	public static InteractionHand guardHand(ItemStack mainHand, ItemStack offHand) {
		if (offHand.has(DataComponents.BLOCKS_ATTACKS)) {
			return InteractionHand.OFF_HAND;
		}

		if (mainHand.has(DataComponents.BLOCKS_ATTACKS)
				|| mainHand.is(GrandCraftTags.GUARD_IMPLEMENTS)) {
			return InteractionHand.MAIN_HAND;
		}

		return null;
	}

	/**
	 * Starts raising the guard.
	 *
	 * @return false when the guard was refused, in which case nothing changed
	 */
	public boolean beginGuard(LivingEntity entity, CombatProfile profile) {
		if (!canStartGuard(entity, profile)) {
			return false;
		}

		this.guardHeld = true;
		this.guardHoldTicks = CombatConstants.GUARD_HOLD_TIMEOUT_TICKS;
		enter(entity, CombatState.GUARD_RAISE, profile.block().raiseTicks());
		return true;
	}

	/**
	 * Renews the hold, on hearing from a client that is still holding its key.
	 *
	 * <p>Only meaningful while the guard is coming up or up: once the tail has begun
	 * the actor has already let go, and a late keepalive must not pull it back.
	 */
	public void refreshGuardHold() {
		if (this.state == CombatState.GUARD_RAISE || this.state == CombatState.GUARDING) {
			this.guardHoldTicks = CombatConstants.GUARD_HOLD_TIMEOUT_TICKS;
		}
	}

	/**
	 * The actor has let go. Drops into the tail immediately rather than waiting for
	 * the current lease to expire, so releasing feels like releasing.
	 */
	public void releaseGuard(LivingEntity entity, CombatProfile profile) {
		dropGuardHold();

		if (this.state == CombatState.GUARD_RAISE || this.state == CombatState.GUARDING) {
			enter(entity, CombatState.GUARD_RECOVERY, profile.block().recoveryTicks());
		}
	}

	/** Whether the guard is up and absorbing. The raise and the tail are not. */
	public boolean isGuarding() {
		return this.state == CombatState.GUARDING;
	}

	/**
	 * Whether an incoming hit is one the guard covers.
	 *
	 * <p>Reproduces vanilla's own shield arithmetic rather than inventing a second
	 * one: the actor's head yaw flattened to horizontal, against the direction the
	 * damage came from, compared to a half-angle. Pitch is deliberately not part of
	 * it, exactly as in vanilla — looking at your feet does not drop your guard.
	 *
	 * <p>Damage with no position at all is never covered. That is vanilla's answer
	 * too, and it is the right one: a guard is a direction, and something with no
	 * direction cannot be facing it.
	 *
	 * <p><strong>Anything inside the actor's own space is always covered</strong>, and
	 * this is a deliberate departure from vanilla. Vanilla compares raw positions no
	 * matter how close they are, which is fine for a shield but not for a mob that
	 * closes to touching distance and stays there: once the hitboxes overlap, the
	 * horizontal offset is a fraction of a block and its <em>direction</em> is
	 * whatever sub-block jitter happens to be that tick. The angle it produces is
	 * then effectively random, so a guard held straight at an attacker standing on
	 * top of you would block roughly at a coin flip. Below touching distance there is
	 * no meaningful "behind", so the arc test is skipped rather than answered badly.
	 */
	public static boolean coversDirection(LivingEntity entity, CombatProfile profile,
			DamageSource source) {
		Vec3 sourcePos = source.getSourcePosition();

		if (sourcePos == null) {
			return false;
		}

		Vec3 offset = sourcePos.subtract(entity.position());
		double distanceSqr = offset.x * offset.x + offset.z * offset.z;
		double touching = touchingDistance(entity, source.getDirectEntity());

		if (distanceSqr <= touching * touching) {
			return true;
		}

		Vec3 flattened = new Vec3(offset.x, 0.0, offset.z);
		Vec3 facing = entity.calculateViewVector(0.0F, entity.getYHeadRot());
		double angle = Math.acos(Math.clamp(flattened.normalize().dot(facing), -1.0, 1.0));

		return angle <= profile.block().arcRadians();
	}

	/**
	 * How close the two have to be before their hitboxes are in contact, which is the
	 * point past which direction stops meaning anything.
	 *
	 * <p>Derived from the widths rather than fixed, so a wide attacker is judged to be
	 * touching from further out — which is exactly what its hitbox does.
	 */
	private static double touchingDistance(LivingEntity entity, Entity attacker) {
		double attackerWidth = attacker == null ? 0.0 : attacker.getBbWidth();

		return (entity.getBbWidth() + attackerWidth) / 2.0;
	}

	/**
	 * Charges for one hit the guard stopped, and breaks the guard if that emptied the
	 * pool.
	 *
	 * <p>Spent with {@link StaminaPool#drain} rather than {@code spend} deliberately:
	 * drain never refuses and takes whatever is left, which is exactly the rule that
	 * the hit which empties the pool is still absorbed. The punishment for
	 * misjudging the budget is the break that follows, not damage leaking through a
	 * guard that was up.
	 */
	public void absorbBlockedHit(LivingEntity entity, CombatProfile profile, float amount) {
		// Before the stamina gate, because an actor that pays no stamina still wears
		// out what it is blocking with.
		wearGuardImplement(entity, amount);

		if (!profile.usesStamina()) {
			return;
		}

		float cost = amount * profile.block().costPerDamageScale();

		if (guardsWithShield(entity)) {
			cost *= profile.block().shieldCostScale();
		}

		this.stamina.drain(profile.stamina(), cost);
		reportStaminaSoon();

		if (this.stamina.exhausted()) {
			breakGuard(entity, profile);
		}
	}

	/**
	 * Whether an off-hand shield is doing the blocking rather than a held weapon.
	 *
	 * <p>Read at the moment it matters rather than latched when the guard went up, so
	 * swapping the off-hand mid-guard needs no bookkeeping and there is no stored
	 * answer that could disagree with the item actually there. The client is never
	 * asked — it only ever reports that a key is held.
	 *
	 * <p>Keyed off the component vanilla itself uses to mark something as a shield, so
	 * modded shields work with no item type named here — and asked of whichever hand
	 * {@link #guardHand} picked, so a shield counts as one in either.
	 */
	public static boolean guardsWithShield(LivingEntity entity) {
		InteractionHand hand = guardHand(entity);

		return hand != null && entity.getItemInHand(hand).has(DataComponents.BLOCKS_ATTACKS);
	}

	/**
	 * Wears down whatever took the hit.
	 *
	 * <p>Removing vanilla's blocking took its durability cost with it — that lived in
	 * {@code BlocksAttacks.hurtBlockingItem}, which our {@code applyItemBlocking} hook
	 * now skips — so the guard has to charge it itself, or a shield would block for
	 * free forever. The formula is vanilla's own, so a shield lasts about as long as
	 * it used to.
	 *
	 * <p>Falls on the hand that is actually guarding: the off-hand when a shield is
	 * doing the work, the main hand when it is a weapon.
	 */
	private static void wearGuardImplement(LivingEntity entity, float amount) {
		if (amount <= 0.0F) {
			return;
		}

		InteractionHand hand = guardHand(entity);

		if (hand == null) {
			return;
		}

		int wear = (int) (CombatConstants.GUARD_WEAR_BASE + CombatConstants.GUARD_WEAR_FACTOR * amount);

		if (wear > 0) {
			entity.getItemInHand(hand).hurtAndBreak(wear, entity, hand);
		}
	}

	/**
	 * Shatters the guard, throwing the actor into a stagger with it down.
	 *
	 * <p>Deliberately does <em>not</em> go through {@link #applyStagger}. That routes
	 * through the stagger tracker, which suppresses the third consecutive reaction so
	 * that pressure cannot stunlock — correct for ordinary hits, and wrong here. A
	 * guard break is a mistake the actor made with its own resource, and it has to be
	 * punished every single time or the resource stops mattering.
	 */
	private void breakGuard(LivingEntity entity, CombatProfile profile) {
		dropGuardHold();

		int ticks = profile.block().breakTicks();

		// Modifiers only when there is a timer to clear them: they are removed on the
		// tick the stagger counter reaches zero, which never happens if it never ran.
		if (ticks > 0) {
			this.staggerTicks = ticks;
			applyStaggerModifiers(entity,
					-CombatConstants.STAGGER_SLOW, -CombatConstants.STAGGER_JUMP_PENALTY);
		}

		enter(entity, CombatState.STAGGERED, ticks);
	}

	/**
	 * Drains the cost of standing behind the guard, and breaks it once the pool runs
	 * out.
	 *
	 * <p>Continuous like the sprint drain, and through {@link StaminaPool#drain} for
	 * the same reason: it pushes the regen delay back every tick it runs, so
	 * <strong>nothing recovers while a guard is up</strong>. That is the rule, not a
	 * side effect — a guard is a commitment with a finite budget, and getting the pool
	 * back means dropping it and making space rather than waiting behind it. It also
	 * means the hold cost is doing two jobs, and setting it to zero would quietly
	 * restore regen behind the guard rather than merely making the hold free.
	 */
	private void drainGuard(LivingEntity entity, CombatProfile profile) {
		if (this.state != CombatState.GUARDING) {
			return;
		}

		float cost = profile.block().holdCostPerTick();

		if (cost <= 0.0F) {
			return;
		}

		this.stamina.drain(profile.stamina(), cost);

		if (this.stamina.exhausted()) {
			breakGuard(entity, profile);
		}
	}

	/** Forgets the hold without touching the state machine. */
	private void dropGuardHold() {
		this.guardHeld = false;
		this.guardHoldTicks = 0;
	}

	/**
	 * Read at the moment the guard is dropped rather than stored when it began, so an
	 * admin lengthening the tail mid-fight takes effect on the next guard rather than
	 * the one after. Falls back to no tail if the profile vanished.
	 */
	private static int guardRecoveryTicks(LivingEntity entity) {
		CombatProfile profile = CombatProfiles.forEntity(entity);

		return profile == null ? 0 : profile.block().recoveryTicks();
	}

	/**
	 * Keeps the guard's movement penalty matching the current state.
	 *
	 * <p>Called from both terminal points of a transition — {@link #enter} for a phase
	 * that holds, {@link #returnToNeutral} for the end of one — so every route out of
	 * a guard clears it: releasing, breaking, being staggered, or rolling out.
	 */
	private void syncGuardSlow(LivingEntity entity) {
		if (this.state != CombatState.GUARDING) {
			remove(entity, Attributes.MOVEMENT_SPEED, GUARD_SPEED_ID);
			return;
		}

		CombatProfile profile = CombatProfiles.forEntity(entity);

		if (profile != null) {
			scale(entity, Attributes.MOVEMENT_SPEED, GUARD_SPEED_ID,
					-profile.block().moveSlowFraction());
		}
	}

	/**
	 * Throws the actor along the dodge as a single impulse.
	 *
	 * <p>Applied server side and pushed with {@code hurtMarked}, which is the same
	 * route knockback takes. Doing it here rather than on the client is what keeps
	 * the server's idea of where the player is going in step with the client's —
	 * a client-side launch of this size reads as moving too quickly and gets pulled
	 * back. Vertical motion is left alone so the roll stays a roll rather than a hop.
	 *
	 * <p>One impulse, not a push every tick: physics carries it and decays it, which
	 * both looks better than a constant-velocity slide and avoids fighting the
	 * client's own movement for the duration.
	 */
	private static void launch(LivingEntity entity, CombatProfile profile, Vec3 direction) {
		double speed = profile.dodge().speedPerTick();

		// Flattened before it is normalised, not after. A fallback direction taken from
		// where the actor is looking can be steeply up or down, and normalising that
		// first would leave almost nothing in the horizontal components and produce a
		// dodge that barely moves.
		double lengthSqr = direction.x * direction.x + direction.z * direction.z;

		if (speed <= 0.0 || !(lengthSqr > 0.0) || !Double.isFinite(lengthSqr)) {
			return;
		}

		double scale = speed / Math.sqrt(lengthSqr);

		entity.setDeltaMovement(direction.x * scale, entity.getDeltaMovement().y, direction.z * scale);

		// Server-applied movement is only sent to clients when this is set.
		entity.hurtMarked = true;
	}

	private void spendDodgeCost(CombatProfile profile) {
		if (!profile.usesStamina()) {
			return;
		}

		this.stamina.spend(profile.stamina(), profile.dodge().cost());
		reportStaminaSoon();
	}

	private void spendAttackCost(CombatProfile profile) {
		if (!profile.usesStamina()) {
			return;
		}

		this.stamina.spend(profile.stamina(), profile.stamina().attackCost());
		reportStaminaSoon();
	}

	/**
	 * Charges for a jump.
	 *
	 * @return false when the actor could not afford it. Meaningful only for actors
	 *         the server moves: a player's jump is predicted and already reported by
	 *         the time this runs, so there is nothing left to refuse — the client
	 *         gate is what actually stops those.
	 */
	public boolean spendJump(CombatProfile profile) {
		if (!profile.usesStamina() || profile.stamina().jumpCost() <= 0) {
			return true;
		}

		boolean paid = this.stamina.spend(profile.stamina(), profile.stamina().jumpCost());
		reportStaminaSoon();
		return paid;
	}

	/**
	 * Drains the cost of sprinting, and cuts the sprint off once the pool is spent.
	 *
	 * <p>Continuous rather than charged once, so sprinting refreshes the regen delay
	 * every tick it runs and a pool cannot refill while it is being spent.
	 */
	private void drainSprint(LivingEntity entity, CombatProfile profile) {
		if (!entity.isSprinting()) {
			return;
		}

		float cost = profile.stamina().sprintCostPerTick();

		if (cost <= 0.0F) {
			return;
		}

		this.stamina.drain(profile.stamina(), cost);

		if (this.stamina.exhausted()) {
			// Syncs to the owner's client through entity data. The client refuses to
			// start sprinting again while exhausted, which is what stops the two from
			// arguing about it every tick.
			entity.setSprinting(false);
		}
	}

	/**
	 * Lets the next tick report the pool instead of holding it for the routine
	 * interval. Used for the discrete costs, which happen while handling a packet and
	 * are exactly the changes a player is looking for confirmation of.
	 */
	private void reportStaminaSoon() {
		this.staminaSyncDelay = 0;
	}

	/**
	 * Tells the owner's client about its pool when the copy it holds has gone stale.
	 *
	 * <p>Only players have a bar to draw, so nothing is tracked for mobs — otherwise
	 * every mob in the world would run this bookkeeping to reach a send that is
	 * immediately discarded.
	 */
	private void syncStamina(LivingEntity entity, CombatProfile profile) {
		if (!(entity instanceof ServerPlayer player)) {
			return;
		}

		if (this.staminaSyncDelay > 0) {
			this.staminaSyncDelay--;
		}

		if (!staminaSyncDue()) {
			return;
		}

		this.syncedStamina = this.stamina.current();
		this.syncedExhausted = this.stamina.exhausted();
		this.staminaSyncDelay = CombatConstants.STAMINA_SYNC_INTERVAL_TICKS;

		ServerPlayNetworking.send(player, new StaminaPayload(
				this.stamina.current(),
				profile.stamina().maxStamina(),
				profile.stamina().regenPerSecond(),
				this.stamina.regenDelay(),
				profile.stamina().jumpCost(),
				this.stamina.exhausted()));
	}

	private boolean staminaSyncDue() {
		// A change of state is worth a packet immediately: it changes what the bar
		// means rather than only how full it is, and the client must not keep telling
		// the player they may act when the server has stopped them.
		if (this.stamina.exhausted() != this.syncedExhausted) {
			return true;
		}

		if (this.staminaSyncDelay > 0) {
			return false;
		}

		return Math.abs(this.stamina.current() - this.syncedStamina)
				>= CombatConstants.STAMINA_SYNC_EPSILON;
	}

	/**
	 * Applies the hit reaction for one qualifying hit.
	 *
	 * <p>A suppressed stagger is not a suppressed hit: damage, sound, particles
	 * and knockback have already happened by the time this is called.
	 */
	public void applyStagger(LivingEntity entity) {
		CombatProfile profile = CombatProfiles.forEntity(entity);

		if (profile == null) {
			return;
		}

		int level = this.stagger.registerHit();

		if (level == 0) {
			return;
		}

		StaggerProfile staggerProfile = profile.stagger();

		this.staggerTicks = staggerProfile.ticks(level);
		applyStaggerModifiers(entity, staggerProfile.speedPenalty(level), staggerProfile.jumpPenalty());

		if (this.state == CombatState.NEUTRAL || this.state == CombatState.ATTACK_STARTUP
				|| this.state == CombatState.GUARD_RAISE || this.state == CombatState.GUARDING) {
			// Cancels a wind-up; a committed attack is left alone. See class docs.
			//
			// A guard is knocked down for the same reason a wind-up is cancelled: the
			// only hits that reach here while one is up are the ones it did not cover —
			// from behind, or of a kind it cannot stop — and those should put it down.
			// The tail is not included, which keeps it as committed as attack recovery.
			this.attack = null;
			this.activeHitConsumed = false;
			dropGuardHold();
			enter(entity, CombatState.STAGGERED, this.staggerTicks);
		}
	}

	private void advanceState(LivingEntity entity) {
		switch (this.state) {
			case ATTACK_STARTUP -> enter(entity, CombatState.ATTACK_ACTIVE, this.attack.activeTicks());
			case ATTACK_ACTIVE -> enter(entity, CombatState.ATTACK_RECOVERY, this.attack.recoveryTicks());
			case DODGE_ACTIVE -> enter(entity, CombatState.DODGE_RECOVERY, dodgeRecoveryTicks(entity));
			case GUARD_RAISE -> enter(entity, CombatState.GUARDING, CombatConstants.GUARD_LEASE_TICKS);
			// The lease ran out. Renew it while the actor is still asking; otherwise
			// this is how a guard whose release was never heard puts itself down.
			case GUARDING -> {
				if (this.guardHeld) {
					enter(entity, CombatState.GUARDING, CombatConstants.GUARD_LEASE_TICKS);
				} else {
					enter(entity, CombatState.GUARD_RECOVERY, guardRecoveryTicks(entity));
				}
			}
			case ATTACK_RECOVERY, STAGGERED, DODGE_RECOVERY, GUARD_RECOVERY ->
					returnToNeutral(entity);
			case NEUTRAL -> {
			}
		}
	}

	/**
	 * Read at the moment the dodge's protected half ends rather than stored when it
	 * began, so an admin lengthening the tail mid-fight takes effect on the next
	 * dodge rather than the one after. Falls back to no tail if the profile vanished.
	 */
	private static int dodgeRecoveryTicks(LivingEntity entity) {
		CombatProfile profile = CombatProfiles.forEntity(entity);

		return profile == null ? 0 : profile.dodge().recoveryTicks();
	}

	private void enter(LivingEntity entity, CombatState next, int ticks) {
		this.state = next;
		this.stateTicks = Math.max(ticks, 0);
		this.phaseTotalTicks = this.stateTicks;

		// A zero-length phase falls straight through rather than sticking, and is
		// never announced — a phase with no duration has nothing to animate, and the
		// phase that does hold sends for itself a moment later.
		// Terminates because the states that lead to returnToNeutral do not re-enter.
		if (this.stateTicks == 0) {
			advanceState(entity);
			return;
		}

		syncGuardSlow(entity);
		syncPhase(entity);
	}

	private void returnToNeutral(LivingEntity entity) {
		this.state = CombatState.NEUTRAL;
		this.stateTicks = 0;
		this.phaseTotalTicks = 0;
		this.attack = null;
		this.activeHitConsumed = false;

		// A guard that reaches neutral is over, however it got here. A client still
		// holding its key raises a fresh one on its next keepalive, which is what lets
		// a held guard come back by itself after a stagger — but only once the gates
		// that refused it in the first place have cleared.
		dropGuardHold();
		syncGuardSlow(entity);
		syncPhase(entity);
	}

	/**
	 * Tells everyone who can see this actor which phase it just entered.
	 *
	 * <p>Sent on transitions rather than streamed per tick, the same bargain
	 * {@link com.hrtq.grandcraft.network.AttackLockoutPayload} makes: the client is
	 * given the length of the phase and animates it out itself. A whole attack is
	 * four packets — startup, active, recovery, neutral — against a cycle of roughly
	 * seventeen ticks.
	 *
	 * <p>Unlike every other send in the mod this is scoped to <em>observers</em>, not
	 * to the actor. A wind-up that only its owner could see would be useless: the
	 * telegraph exists to be read by the other side of the fight.
	 */
	private void syncPhase(LivingEntity entity) {
		if (!(entity.level() instanceof ServerLevel)) {
			return;
		}

		if (this.state == CombatState.NEUTRAL) {
			// Returning to neutral is only worth a packet if the phase that just ended
			// was one the client was told about. Deciding this on what was actually
			// announced rather than on whether neutral itself is animated is what stops
			// an actor being left mid-pose: an actor can be animated for one phase and
			// not another — the player is drawn dodging but not swinging — and the
			// end of the drawn one still has to be reported.
			if (!this.syncedPhaseActive) {
				return;
			}

			this.syncedPhaseActive = false;
		} else {
			if (!animatesPhases(entity, this.state)) {
				return;
			}

			this.syncedPhaseActive = true;
		}

		CombatPhasePayload payload = phasePayload(entity);

		for (ServerPlayer viewer : PlayerLookup.tracking(entity)) {
			ServerPlayNetworking.send(viewer, payload);
		}

		// PlayerLookup.tracking excludes the subject itself, and a player has to see
		// their own commitment in third person. Costs nothing until the player gains
		// real attack phases.
		if (entity instanceof ServerPlayer self) {
			ServerPlayNetworking.send(self, payload);
		}
	}

	/**
	 * Sends the phase in flight to one viewer who has just started tracking this
	 * actor, so a wind-up already underway when it came into view is still legible.
	 *
	 * <p>The payload carries remaining and total separately for exactly this case:
	 * on a transition the two are equal, here they are not.
	 */
	public void syncPhaseTo(LivingEntity entity, ServerPlayer viewer) {
		if (this.state == CombatState.NEUTRAL || !animatesPhases(entity, this.state)) {
			return;
		}

		ServerPlayNetworking.send(viewer, phasePayload(entity));
	}

	/**
	 * Whether this actor's phases are worth drawing.
	 *
	 * <p>The same flag that decides whether the phase timers govern the actor's
	 * melee at all: if they do, the phases are the attack and the animation layer
	 * owns the whole swing. If they do not — the player today, whose damage still
	 * lands on vanilla timing and whose only phase is a bookkeeping recovery — then
	 * vanilla is still drawing that swing, and announcing a phase would let the
	 * client suppress an animation it has no replacement for.
	 *
	 * <p>The player joins this the moment it gains real attack phases, by gaining
	 * the verb. Nothing here names an entity type.
	 *
	 * <p>Dodge, guard and stagger phases are the exception and are always drawn:
	 * vanilla has no roll to be in competition with, its own blocking is gone, and its
	 * hit reaction is a red flash rather than a pose — so in none of the three is
	 * there anything to suppress or anything that could be left half-drawn. This is
	 * what lets the player be animated for those while its swing is still vanilla's to
	 * draw, and without it the player, which has no {@link CombatVerb#PHASED_MELEE},
	 * would never be told about its own guard or its own stagger at all.
	 *
	 * <p>Stagger in particular has to be here: a guard break puts the player straight
	 * into one, and a punishment nobody can see does not read as a punishment.
	 */
	private static boolean animatesPhases(LivingEntity entity, CombatState state) {
		if (state.isDodge() || state.isGuard() || state == CombatState.STAGGERED) {
			return true;
		}

		CombatProfile profile = CombatProfiles.forEntity(entity);

		return profile != null && profile.actor().usesMeleeGoal();
	}

	private CombatPhasePayload phasePayload(LivingEntity entity) {
		return new CombatPhasePayload(
				entity.getId(), this.state.ordinal(), this.stateTicks, this.phaseTotalTicks);
	}

	private void applyStaggerModifiers(LivingEntity entity, double speedPenalty, double jumpPenalty) {
		scale(entity, Attributes.MOVEMENT_SPEED, STAGGER_SPEED_ID, speedPenalty);

		// Movement speed governs ground acceleration, so without this a staggered
		// actor can jump and carry its pre-stagger momentum straight through the
		// slowdown. Cutting jump strength shortens that airtime.
		scale(entity, Attributes.JUMP_STRENGTH, STAGGER_JUMP_ID, jumpPenalty);
	}

	private void clearStaggerModifiers(LivingEntity entity) {
		remove(entity, Attributes.MOVEMENT_SPEED, STAGGER_SPEED_ID);
		remove(entity, Attributes.JUMP_STRENGTH, STAGGER_JUMP_ID);
	}

	/**
	 * Keeps the modifiers this actor holds at all times matching the current tuning.
	 *
	 * <p>Knockback resistance is held permanently rather than applied on hit because
	 * knockback resolves during the damage event — by the time a stagger is applied
	 * the target is already moving. Speed and damage are permanent for the simpler
	 * reason that they describe the actor rather than a reaction.
	 *
	 * <p>Only touches the attributes when the profile object actually changes, so the
	 * per-tick cost is one reference comparison.
	 */
	private void syncPermanentModifiers(LivingEntity entity, CombatProfile profile) {
		RolledStats stats = entity.getAttachedOrElse(
				GrandCraftAttachments.ROLLED_STATS, RolledStats.VANILLA);

		if (profile == this.appliedProfile && stats.equals(this.appliedStats)) {
			return;
		}

		this.appliedProfile = profile;
		this.appliedStats = stats;

		// Base knockback resistance is 0 and vanilla scales knockback by
		// (1 - resistance), so this adds a flat value rather than multiplying.
		add(entity, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID,
				CombatConstants.KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE);

		// Rolled multipliers are "1.0 means vanilla", and ADD_MULTIPLIED_TOTAL scales
		// the finished value, so armour, effects and a held weapon are all scaled
		// rather than replaced. Sitting on MOVEMENT_SPEED alongside the stagger
		// modifier is fine: the two have different ids and compose, so a staggered
		// fast actor is fast then slowed.
		scale(entity, Attributes.MAX_HEALTH, HEALTH_ID, stats.health() - 1.0);
		scale(entity, Attributes.ATTACK_DAMAGE, DAMAGE_ID, stats.damage() - 1.0);
		scale(entity, Attributes.MOVEMENT_SPEED, SPEED_ID, stats.speed() - 1.0);

		// Vanilla mobs have no base armour, so a multiplier of it would always be
		// zero. Defence is absolute points for that reason.
		add(entity, Attributes.ARMOR, DEFENCE_ID, stats.defence(),
				AttributeModifier.Operation.ADD_VALUE);
	}

	/**
	 * Forces the permanent modifiers onto the entity now, rather than waiting for the
	 * next tick to notice a change.
	 *
	 * <p>Used at spawn, where the stats have just been rolled and the caller needs
	 * max health to already reflect them before topping the entity up.
	 */
	public void refreshModifiers(LivingEntity entity) {
		CombatProfile profile = CombatProfiles.forEntity(entity);

		if (profile == null) {
			return;
		}

		this.appliedProfile = null;
		syncPermanentModifiers(entity, profile);
	}

	private static void add(LivingEntity entity, Holder<Attribute> attribute, Identifier id,
			double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = entity.getAttribute(attribute);

		if (instance != null) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}

	/** Scales the finished attribute value: 0 leaves it alone, -0.2 takes a fifth off. */
	private static void scale(LivingEntity entity, Holder<Attribute> attribute,
			Identifier id, double fraction) {
		add(entity, attribute, id, fraction, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static void remove(LivingEntity entity, Holder<Attribute> attribute, Identifier id) {
		AttributeInstance instance = entity.getAttribute(attribute);

		if (instance != null) {
			instance.removeModifier(id);
		}
	}
}
