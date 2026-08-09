package com.hrtq.grandcraft.combat;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.effect.ManaRegenEffect;
import com.hrtq.grandcraft.network.CombatPhasePayload;
import com.hrtq.grandcraft.network.ManaPayload;
import com.hrtq.grandcraft.network.StaminaPayload;
import com.hrtq.grandcraft.player.GrandCraftAttachments;
import com.hrtq.grandcraft.progression.EssenceAwards;
import com.hrtq.grandcraft.stats.GrandCraftAttributes;
import com.hrtq.grandcraft.stats.ManaPool;
import com.hrtq.grandcraft.stats.ManaSettings;
import com.hrtq.grandcraft.stats.PoolBlock;
import com.hrtq.grandcraft.stats.StaminaScaling;
import com.hrtq.grandcraft.stats.StatEffects;
import com.hrtq.grandcraft.stats.StatSettings;
import com.hrtq.grandcraft.stats.StatTuning;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Direction;
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

	/**
	 * The movement bonus of an empowerment window — Combat Master's, today.
	 *
	 * <p>Its own identifier rather than sharing {@link #GUARD_SPEED_ID}, so the two
	 * compose instead of overwriting: a Warrior who blocks, gains the window and then
	 * raises their guard again should be slowed by the guard <em>and</em> quickened by
	 * the window, not whichever was applied last.
	 */
	private static final Identifier EMPOWER_SPEED_ID = GrandCraft.id("empower_speed");

	/**
	 * Cancels gravity while an actor is hanging on a wall — the Outlaw's Acrobat, today.
	 *
	 * <p>An attribute rather than {@code Entity.setNoGravity}, which writes
	 * {@code NoGravity} into the entity's NBT: a session that ended badly mid-hang would
	 * bring the player back permanently airborne. A transient modifier dies with the
	 * entity, which is the failure mode worth having.
	 *
	 * <p>{@code Attributes.GRAVITY} is registered syncable in 26.2, so this reaches the
	 * owning client and its own {@code LocalPlayer} physics stop falling too. That is the
	 * whole reason the wall hang needs no movement mixin and no prediction protocol: both
	 * sides compute the same thing from the same number.
	 *
	 * <p><strong>The one composition that inverts rather than degrades.</strong>
	 * {@code ADD_MULTIPLIED_TOTAL} sums its fractions and multiplies once, so the -1.0
	 * here lands on exactly zero — but a second multiplied-total modifier on GRAVITY
	 * would push the sum below -1 and give <em>negative</em> gravity, i.e. a player
	 * falling upwards. Nothing in vanilla 26.2 does that (Slow Falling is a clamp inside
	 * {@code getEffectiveGravity}, not a modifier). If something ever does, the answer is
	 * the {@code onClimbable} route, not a second modifier fighting this one.
	 */
	private static final Identifier WALL_HANG_GRAVITY_ID = GrandCraft.id("wall_hang_gravity");

	/**
	 * Ceiling on {@link #groundedTicks}, which only ever has to answer "has it been long
	 * enough yet". Standing still for an hour must not overflow it into a negative.
	 */
	private static final int GROUNDED_TICKS_CAP = 1200;

	private final StaggerTracker stagger = new StaggerTracker();

	private final StaminaPool stamina = new StaminaPool();

	/**
	 * The mana ceiling bought with attribute points.
	 *
	 * <p>Only the ceiling lives here. The pool itself is persisted on the entity, so
	 * unlike stamina there is no object to hold — {@link ManaPool} is statics over an
	 * attachment and this is the one derived figure it has to be handed.
	 *
	 * <p><strong>Deliberately not part of the saved record.</strong> It is derived from
	 * {@code EssenceProgress.spentPools()} and the stat settings, both already
	 * persisted, so storing it too would be a second source of truth that could go
	 * stale after a reclass. Being a controller field also keeps it transient, which is
	 * what makes writing it on the respawn path safe.
	 */
	private int manaBonusMax;

	/** For turning a per-tick rate back into the per-second figure the client is sent. */
	private static final int TICKS_PER_SECOND = 20;

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
	 * The ceiling as last reported, tracked for the same reason the mana one is: buying
	 * an attribute point moves the maximum without moving the current value, and a
	 * client told only the value would keep drawing the old bar. Starts at a value no
	 * real pool can hold so the first tick always sends.
	 */
	private float syncedStaminaMax = -1.0F;

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

	/**
	 * The stat settings currently expressed on this entity, compared by identity for
	 * the same reason {@link #appliedProfile} is: {@code StatTuning} swaps the whole
	 * immutable object, so a new reference is exactly the signal to re-apply.
	 *
	 * <p>Without this an admin raising the armour-per-Constitution figure would not
	 * move anyone's armour until their Constitution happened to change.
	 */
	private StatSettings appliedStatSettings;

	/**
	 * The stat values those effects were computed from. NaN before the first pass,
	 * which is deliberate — NaN compares unequal to itself, so the first tick always
	 * applies without needing a separate flag.
	 */
	private double appliedConstitution = Double.NaN;
	private double appliedAgility = Double.NaN;

	/**
	 * The attribute points currently expressed as pool ceilings and a health modifier.
	 *
	 * <p>Null before the first pass, which is what makes that pass always run — the
	 * same job {@code Double.NaN} does for the two stats above, in the form a record
	 * reference can take.
	 */
	private PoolBlock appliedPoolPoints;

	/** What this actor's stats do to its stamina. Neutral until a stat pass runs. */
	private StaminaScaling staminaScaling = StaminaScaling.NONE;

	/** Ticks before another routine mana packet may be sent. */
	private int manaSyncDelay;

	/** The pool as the owner's client last heard it. See {@link #syncedStamina}. */
	private float syncedMana = -1.0F;

	/**
	 * The ceiling as last reported. Tracked separately from the value because an admin
	 * switching mana off drops the maximum to zero without moving the current amount,
	 * and a client told only about the value would leave the old pool on screen.
	 */
	private float syncedManaMax = -1.0F;

	/**
	 * The effective recovery rate as last reported.
	 *
	 * <p>Compared alongside the value and the ceiling, because the client
	 * <em>extrapolates</em> the bar forward from this figure between packets — so a
	 * rate that changes while the pool sits still is a change the client cannot see
	 * and will not stop applying.
	 *
	 * <p>Found in game: a Sorcerer whose mana was recovering kept on recovering after
	 * being reclassed below the Arcane threshold, until something moved the value and
	 * forced a packet. The pool had stopped on the server the whole time; only the
	 * client was still counting up.
	 *
	 * <p>Third instance of one pattern — the other two were the ceiling moving without
	 * the value when a pool point was bought. <strong>Anything the payload carries that
	 * the client acts on has to be part of the staleness test</strong>, not just the
	 * headline number. Starts at -1 so the first send can never be skipped.
	 */
	private int syncedManaRegen = -1;

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
			clearEmpowerment(entity);

			// Not optional. A stuck gravity modifier is permanent flight on a live
			// entity, which is the worst thing anything in this class can leak.
			clearWallHook(entity);
			return;
		}

		tickEmpowerment(entity);

		syncPermanentModifiers(entity, profile);

		// Before the stamina block, because it is what sets the scaling that block
		// spends through.
		syncStatEffects(entity);
		this.stagger.tick(profile.stagger().resetTicks());

		// Before the stamina block too, and for a related reason: tickWallHook is what
		// ends a grip on landing, and the hang must not be charged for a tick it did not
		// finish hanging through.
		tickGroundContact(entity);
		tickWallHook(entity);

		// Skipped entirely for an actor without stamina. Every stamina gate short
		// circuits on the same usesStamina() check, so its pool is never read either.
		if (profile.usesStamina()) {
			this.stamina.tick(profile.stamina(), this.staminaScaling.regenMultiplier());
			drainSprint(entity, profile);
			drainGuard(entity, profile);

			// Beside the other continuous drains and before the sync, so the packet
			// describes a pool the drain has already been taken out of and an exhaustion
			// the grip has already been dropped for.
			drainWallHang(entity, profile);
			syncStamina(entity, profile);
		}

		tickMana(entity);

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
		WeaponProfile weapon = weaponProfile(entity, profile);

		if (!canStartAttack(profile, weapon)) {
			return false;
		}

		spendAttackCost(profile, weapon);
		this.attack = weapon.attack();
		this.activeHitConsumed = false;
		enter(entity, CombatState.ATTACK_STARTUP, this.attack.startupTicks());
		return true;
	}

	/**
	 * The weapon governing this actor's next swing.
	 *
	 * <p>An actor without {@link CombatVerb#WEAPONS} is handed its own
	 * {@link ActorSettings} values, so the mob path is exactly what it was before
	 * weapons existed and no caller has to know which kind of actor it is holding.
	 *
	 * <p>Resolved from the main hand only. An off-hand weapon is not what the swing
	 * comes from, and vanilla's own attack damage is main-hand too.
	 *
	 * <p>The wind-up and the endlag are handed <em>in</em> rather than looked up: they are
	 * the actor's own globals, and passing them keeps {@link Weapons} reading only the
	 * weapon config. That matters because weapon settings are pushed to every client and
	 * combat settings are not — so a {@code Weapons} that reached into
	 * {@code CombatTuning} would answer differently on a client than on the server. See
	 * {@code Weapons.startupFor} and {@code Weapons.recoveryFor}.
	 */
	private static WeaponProfile weaponProfile(LivingEntity entity, CombatProfile profile) {
		return profile.usesWeapons()
				? Weapons.profileFor(entity.getMainHandItem(),
						profile.melee().startupTicks(), profile.melee().recoveryTicks())
				: WeaponProfile.fromActor(profile);
	}

	/**
	 * An actor may only start an attack from neutral, never while staggered, and
	 * never without the stamina to pay for it.
	 *
	 * <p>Takes the profile rather than reading a cached one so the stamina gate
	 * cannot be bypassed by calling a no-argument overload — every caller already has
	 * the profile in hand. It now takes the entity for the same reason: the cost
	 * depends on what is being held, and resolving that here rather than at each call
	 * site means a caller cannot accidentally price a swing as if it were unarmed.
	 */
	public boolean canStartAttack(LivingEntity entity, CombatProfile profile) {
		return canStartAttack(profile, weaponProfile(entity, profile));
	}

	private boolean canStartAttack(CombatProfile profile, WeaponProfile weapon) {
		if (this.state != CombatState.NEUTRAL || this.staggerTicks > 0) {
			return false;
		}

		return !profile.usesStamina()
				|| this.stamina.has(profile.stamina(),
						this.staminaScaling.cost(weapon.attackCost()));
	}

	/**
	 * Ticks until the phase and stagger timers stop blocking an attack, or 0 if they
	 * already do not. Both gates have to clear, so this is whichever runs longer.
	 * Used to tell a player's client how long to show its attack indicator unfilled.
	 *
	 * <p><strong>The whole swing, not the phase it is in.</strong> This reported only
	 * {@link #stateTicks} until 2026-08-07, which is the current phase's remainder — so
	 * the packet sent when an attack began described the wind-up alone, and the indicator
	 * filled up during the startup and then sat full through the active window and the
	 * whole endlag, telling the player they could swing for most of the time they could
	 * not. The endlag is exactly the part worth showing: it is the commitment, and it is
	 * the question the indicator is being asked (user, 2026-08-07).
	 *
	 * <p>The phases still to come are read off the latched {@link #attack} rather than
	 * from the settings, so the figure matches the swing actually in flight even if the
	 * weapon is swapped or the config edited mid-swing — the same reason the controller
	 * latches that profile in the first place. Nothing re-sends mid-attack, so the client
	 * gets one span and counts it out: an active window that ends early would make this
	 * an over-estimate, and none can — a spent hit lets the window run on ({@link
	 * #consumeActiveHit}).
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
		//
		// Everything else — the dodge halves, a stagger — is already a single phase that
		// ends with the actor free, so its own remainder is the whole answer.
		int phase = switch (this.state) {
			case NEUTRAL, GUARDING -> 0;
			case ATTACK_STARTUP -> this.stateTicks + remainingAfterStartup();
			case ATTACK_ACTIVE -> this.stateTicks + remainingAfterActive();
			default -> this.stateTicks;
		};

		return Math.max(this.staggerTicks, phase);
	}

	/** The active window and the endlag that a wind-up still has ahead of it. */
	private int remainingAfterStartup() {
		return this.attack == null
				? 0
				: this.attack.activeTicks() + this.attack.recoveryTicks();
	}

	/** The endlag an open window still has ahead of it. */
	private int remainingAfterActive() {
		return this.attack == null ? 0 : this.attack.recoveryTicks();
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


	// enterRecoveryOnly lived here until 2026-08-05. It skipped straight to endlag with no
	// startup and no active window, because the player had no attack pose and a wind-up
	// nobody can see is indistinguishable from lag. Both player paths — the entity click
	// and the swing at air — go through beginAttack now, so there is nothing left that
	// wants an attack with no front half. Deleted rather than kept for a caller that may
	// never come back.

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

		if (!canActFreely()) {
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
				|| this.stamina.has(profile.stamina(),
						this.staminaScaling.cost(profile.dodge().cost()));
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
	/**
	 * Skips the endlag of a guard that has just been lowered, so an attack can follow
	 * it immediately.
	 *
	 * <h2>You cannot attack through a raised guard</h2>
	 * Deliberately acts on {@link CombatState#GUARD_RECOVERY} <em>only</em>. A raised
	 * guard is a commitment: while it is up the actor is defending and not attacking,
	 * and {@link #canStartAttack} refuses the swing because GUARDING is not NEUTRAL.
	 * Dropping the guard is a decision the player has to make and time, which is where
	 * the skill in "block, then punish" lives (user's call, 2026-08-02). An earlier
	 * version lowered the guard automatically on a click, which let a player hold
	 * block permanently and swing out of it for free — defence with no downside, and
	 * exactly the kind of dominant option a fight should not have.
	 *
	 * <h2>But dropping it must not cost a beat</h2>
	 * What is skipped is only the endlag for <em>lowering</em> the guard. That endlag
	 * exists to stop a guard flickering on and off; taxing the counter-attack with it
	 * was an accident of the same timer. It mattered because a refused attack is not
	 * queued — the click is swallowed — so the natural motion of letting go and
	 * clicking landed inside those few ticks, did nothing at all, and the punish
	 * window closed. Reading an attack correctly should be rewarded, not charged
	 * reaction time.
	 *
	 * <p>The guard's endlag still applies to everything else, including raising the
	 * guard again, so the flicker it was written to prevent is still prevented.
	 *
	 * <p>Refuses when the swing could not happen anyway — staggered, or too little
	 * stamina to pay for it — so a click that was never going to connect does not
	 * quietly consume the endlag either. Note the stamina arm of that is
	 * <em>silent</em>: the click does nothing and nothing says why. The stamina bar is
	 * the only tell, which is fine while the costs are legible and worth revisiting if
	 * it is ever reported.
	 *
	 * @return true when guard endlag was actually skipped
	 */
	public boolean cancelGuardEndlagForAttack(LivingEntity entity, CombatProfile profile) {
		if (this.state != CombatState.GUARD_RECOVERY) {
			return false;
		}

		if (this.staggerTicks > 0) {
			return false;
		}

		if (profile.usesStamina() && !this.stamina.has(profile.stamina(),
				this.staminaScaling.cost(weaponProfile(entity, profile).attackCost()))) {
			return false;
		}

		// The guard is already down by this point — this only ends the tail early.
		// returnToNeutral rather than a bare state write, because it is what re-syncs
		// the movement penalty and tells watching clients the pose is over.
		returnToNeutral(entity);
		return true;
	}

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

	// ------------------------------------------------------------- empowerment

	/**
	 * Ticks left of an empowerment window — the state behind the Warrior's Combat
	 * Master, and whatever else grants one later.
	 *
	 * <p>Deliberately mechanical. This class holds the countdown and the movement
	 * modifier and nothing else: <em>who</em> earns a window, what opens one, what
	 * closes one and what an empowered blow is worth all live in
	 * {@code skill/CombatMaster}. The controller never learns what a Warrior is, which
	 * is what stops per-class rules leaking into the one object every actor in the game
	 * owns.
	 *
	 * <p>Transient like the rest of this class. A window is at most a few seconds and
	 * survives neither logout nor death, which is correct — it is earned in a fight and
	 * spent in the same one.
	 */
	private int empowerTicks;

	/**
	 * Opens a window of the given length, replacing any already running.
	 *
	 * <p>Replacing rather than extending: blocking twice in quick succession should
	 * give a full window from the second block, not a longer one. The alternative lets
	 * a Warrior behind a shield bank an arbitrarily long buff.
	 */
	public void grantEmpowerment(LivingEntity entity, int ticks, double speedFraction) {
		if (ticks <= 0) {
			return;
		}

		this.empowerTicks = ticks;

		if (speedFraction != 0.0) {
			scale(entity, Attributes.MOVEMENT_SPEED, EMPOWER_SPEED_ID, speedFraction);
		}
	}

	public boolean isEmpowered() {
		return this.empowerTicks > 0;
	}

	/** Ends the window now, however much was left. Safe to call when none is open. */
	public void clearEmpowerment(LivingEntity entity) {
		this.empowerTicks = 0;
		remove(entity, Attributes.MOVEMENT_SPEED, EMPOWER_SPEED_ID);
	}

	/**
	 * Counts the window down and takes the movement bonus off when it runs out.
	 *
	 * <p>Nothing is sent to the client here. It was told the length when the window
	 * opened, so it can see the end coming — only an <em>early</em> close needs a
	 * message, and that is the one route that does not come through this method.
	 */
	private void tickEmpowerment(LivingEntity entity) {
		if (this.empowerTicks > 0 && --this.empowerTicks <= 0) {
			clearEmpowerment(entity);
		}
	}

	// ----------------------------------------------------------- air mobility

	/**
	 * Air-dash charges in hand — the Outlaw's Acrobat, today.
	 *
	 * <p>Refilled by ground contact <em>and</em> by catching a wall, which is what makes
	 * a dash-hook-dash chain possible at all.
	 *
	 * <p><strong>This counter alone would permit unlimited flight.</strong> Every hook
	 * hands the charge back, so nothing here bounds the chain — {@link #wallHooksLeft}
	 * is the only thing that does. The two counters look redundant and are not, and the
	 * day one of them is "simplified" away the failure is total flight.
	 */
	private int airDashCharges;

	/**
	 * Wall grips left before the actor has to settle on the ground again.
	 *
	 * <p>Refilled only by {@link #groundedTicks} reaching the configured dwell — not by
	 * a mere touch, unlike the dash charge. That asymmetry is the design: a landing
	 * gives the dash straight back so a landing player is never left without their move,
	 * but the hooks are what a traversal spends and they cost a moment of standing.
	 */
	private int wallHooksLeft;

	/** Consecutive ticks on the ground. Zeroed the instant the actor leaves it. */
	private int groundedTicks;

	/**
	 * The face of the wall being gripped, pointing from the actor at it, or null.
	 *
	 * <p><strong>This is the hooked flag.</strong> There is deliberately no separate
	 * boolean beside it, because two representations of one fact can disagree and this
	 * one gates gravity.
	 */
	private Direction wallFace;

	/** Stamina a hanging tick costs, as granted. Zero when nothing is hanging. */
	private float wallHangDrainPerTick;

	/** Ticks left before another grip may form. */
	private int wallHookCooldown;

	/** What to stamp on {@link #wallHookCooldown} at the next release, as granted. */
	private int wallHookReleaseCooldown;

	/**
	 * Grips a wall: stops the actor dead, cancels its gravity and starts the hold.
	 *
	 * <p>Deliberately mechanical, the same split {@link #grantEmpowerment} draws. Which
	 * walls count, how many grips an actor gets, what one costs and who is allowed one
	 * are all {@code skill/Acrobat}'s; the controller never learns what an Outlaw is.
	 *
	 * <p><strong>Stopping the actor is not a one-off, and this method cannot do it alone.</strong>
	 * Cancelling gravity only stops downward speed <em>growing</em>, and a player's
	 * position is client-authoritative, so the server can neither see nor correct a
	 * client that is still falling. {@link #tickWallHook} re-asserts a zero velocity
	 * every tick for as long as the grip lasts; the note there records both of the
	 * cheaper attempts that failed and why, because they are the obvious ones.
	 *
	 * <p>The actor is therefore pinned completely — no drift along the wall, and no
	 * pushing off it. The exits are the deliberate ones: dash off, be hit, run the pool
	 * dry, land, or have the wall taken away.
	 */
	public void grantWallHook(LivingEntity entity, Direction face,
			float drainPerTick, int releaseCooldownTicks) {
		this.wallFace = face;
		this.wallHangDrainPerTick = Math.max(drainPerTick, 0.0F);
		this.wallHookReleaseCooldown = Math.max(releaseCooldownTicks, 0);

		entity.setDeltaMovement(Vec3.ZERO);
		entity.hurtMarked = true;

		scale(entity, Attributes.GRAVITY, WALL_HANG_GRAVITY_ID, -1.0);
		entity.resetFallDistance();
	}

	public boolean isWallHooked() {
		return this.wallFace != null;
	}

	public Direction wallHookFace() {
		return this.wallFace;
	}

	public int wallHookCooldown() {
		return this.wallHookCooldown;
	}

	/**
	 * Lets go, however the grip ended. Safe and cheap to call when nothing is hooked,
	 * which matters — {@code Acrobat.tick} calls it unconditionally on every player who
	 * is not an Outlaw, because that is the only route that catches a reclass mid-hang.
	 */
	public void clearWallHook(LivingEntity entity) {
		if (this.wallFace != null) {
			this.wallHookCooldown = this.wallHookReleaseCooldown;
		}

		this.wallFace = null;
		this.wallHangDrainPerTick = 0.0F;
		remove(entity, Attributes.GRAVITY, WALL_HANG_GRAVITY_ID);
	}

	/**
	 * The grip's non-stamina bookkeeping: the cooldown, and the ends that need no world
	 * query. Whether the wall is still there is {@code Acrobat}'s to ask.
	 *
	 * <p>The cooldown counts down whether or not anything is hanging, or a grip released
	 * on the last tick before landing would keep its cooldown forever.
	 */
	private void tickWallHook(LivingEntity entity) {
		if (this.wallHookCooldown > 0) {
			this.wallHookCooldown--;
		}

		if (this.wallFace == null) {
			return;
		}

		// Water is in here rather than left to the wall check because a waterfall down a
		// cliff face satisfies every other condition: the wall is real, the actor is off
		// the ground, and the grip would hold indefinitely for the price of the drain.
		if (entity.onGround() || entity.isDeadOrDying() || entity.isInWater()) {
			clearWallHook(entity);
			return;
		}

		// Every hooked tick, not once at the grip: the landing after a hang must be
		// harmless however long the hang was, and this is one field write.
		entity.resetFallDistance();

		// Pinned outright, unconditionally, every tick — and the two cheaper versions of
		// this that came first were both wrong, so neither is worth trying again.
		//
		// Cancelling gravity only stops downward speed GROWING. The speed the actor
		// arrived with survives, and vanilla sheds vertical motion at just 0.98 a tick
		// (0.5 b/t is still 0.18 five seconds later), so the first cut read in game as a
		// slow decelerating slide rather than a hang.
		//
		// The second cut zeroed it again whenever the velocity was non-zero, which
		// almost never fired: a PLAYER'S POSITION IS CLIENT-AUTHORITATIVE, so the
		// server's deltaMovement holds whatever was last written to it — zero — while
		// the client is still falling. Any correction conditional on the server noticing
		// movement is dead code. The server cannot see this problem; it can only assert
		// against it.
		//
		// Hence: state asserted, not corrected. Twenty small motion packets a second per
		// hanging actor is a real cost and the right one to pay, because it is the only
		// version that does not depend on whose idea of the velocity is correct.
		//
		// The horizontal is zeroed too, which costs the sideways shuffle along a wall.
		// That was never asked for, and a grip that holds beats one that can be slid
		// along. It also keeps the actor exactly where the wall re-check expects, so the
		// grip cannot quietly drift out of its own tolerance.
		entity.setDeltaMovement(Vec3.ZERO);
		entity.hurtMarked = true;
	}

	/**
	 * Charges for the hold, and drops the actor once the pool is spent.
	 *
	 * <p>Continuous like {@link #drainGuard}, and with the same consequence: every
	 * draining tick pushes the regen delay back, so <strong>nothing recovers while
	 * hanging</strong>. A wall hang is a fixed budget rather than a rate contest, which
	 * is what stops it being a resting position.
	 */
	private void drainWallHang(LivingEntity entity, CombatProfile profile) {
		if (this.wallFace == null || this.wallHangDrainPerTick <= 0.0F) {
			return;
		}

		this.stamina.drain(profile.stamina(), this.staminaScaling.cost(this.wallHangDrainPerTick));

		if (this.stamina.exhausted()) {
			clearWallHook(entity);
		}
	}

	/** Counts continuous ground contact, for whatever needs an actor to have settled. */
	private void tickGroundContact(LivingEntity entity) {
		if (!entity.onGround()) {
			this.groundedTicks = 0;
		} else if (this.groundedTicks < GROUNDED_TICKS_CAP) {
			this.groundedTicks++;
		}
	}

	public int groundedTicks() {
		return this.groundedTicks;
	}

	public int airDashCharges() {
		return this.airDashCharges;
	}

	public void setAirDashCharges(int charges) {
		this.airDashCharges = Math.max(charges, 0);
	}

	public int wallHooksLeft() {
		return this.wallHooksLeft;
	}

	public void setWallHooksLeft(int hooks) {
		this.wallHooksLeft = Math.max(hooks, 0);
	}

	/**
	 * Throws the actor along a dash, horizontally and upward at once.
	 *
	 * <p>Separate from {@link #launch} rather than a parameter on it, because the one
	 * thing that differs is the one thing that matters: a dodge preserves the actor's
	 * vertical motion and a dash <strong>replaces</strong> it. Flattening before
	 * normalising is copied from there and is needed for the same reason — the fallback
	 * direction is a look angle, which can be steeply up or down.
	 *
	 * <p>Setting the vertical rather than adding it is what keeps a chain of dashes from
	 * becoming flight, and is also what lets a dash cancel a fall. The price is that
	 * dashing while already rising cuts the climb short; that is correct and is the sort
	 * of thing that gets reported as a bug, so it is written down in
	 * {@code SkillSettings.acrobatDashLiftPerTick}.
	 */
	public static void airDash(LivingEntity entity, Vec3 direction, double speed, double lift) {
		double lengthSqr = direction.x * direction.x + direction.z * direction.z;

		if (speed <= 0.0 || !(lengthSqr > 0.0) || !Double.isFinite(lengthSqr)) {
			return;
		}

		double scale = speed / Math.sqrt(lengthSqr);

		entity.setDeltaMovement(direction.x * scale, lift, direction.z * scale);
		entity.hurtMarked = true;
		entity.resetFallDistance();
	}

	/**
	 * Pays a discrete cost on a skill's behalf.
	 *
	 * <p>The generic form of {@link #spendJump} and {@code spendDodgeCost}: those name
	 * the setting they read, which is right for a verb the controller owns and wrong for
	 * an ability whose numbers live in {@code SkillSettings}. The pool is private, so
	 * this is the only way a skill can reach it.
	 *
	 * @return false when it could not be afforded, in which case nothing was deducted
	 *         and the caller must suppress the action entirely
	 */
	public boolean spendSkillCost(CombatProfile profile, float rawCost) {
		// Tested before scaling, like spendJump: a configured zero means free, and no
		// multiplier should be able to resurrect a cost out of it.
		if (!profile.usesStamina() || rawCost <= 0.0F) {
			return true;
		}

		boolean paid = this.stamina.spend(profile.stamina(), this.staminaScaling.cost(rawCost));
		reportStaminaSoon();
		return paid;
	}

	/**
	 * Takes a penalty that lands whether or not it can be afforded.
	 *
	 * <p>{@code drain} rather than {@code spend} for the reason {@code absorbBlockedHit}
	 * gives: the event has already happened, and a penalty that refused itself because
	 * the pool was low would reward being low.
	 */
	public void drainSkillCost(CombatProfile profile, float rawCost) {
		if (!profile.usesStamina() || rawCost <= 0.0F) {
			return;
		}

		this.stamina.drain(profile.stamina(), this.staminaScaling.cost(rawCost));
		reportStaminaSoon();
	}

	/**
	 * Whether the actor is free to start something — neither committed to a phase nor
	 * staggered.
	 *
	 * <p>Extracted from {@link #canStartDodge}, which still calls it, so the dodge and
	 * every later verb answer the question from one definition instead of two copies
	 * that can drift.
	 */
	public boolean canActFreely() {
		return (this.state == CombatState.NEUTRAL || this.state == CombatState.GUARDING)
				&& this.staggerTicks == 0;
	}

	public boolean staminaExhausted() {
		return this.stamina.exhausted();
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

		this.stamina.drain(profile.stamina(), this.staminaScaling.cost(cost));
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

		this.stamina.drain(profile.stamina(), this.staminaScaling.cost(cost));

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

		this.stamina.spend(profile.stamina(), this.staminaScaling.cost(profile.dodge().cost()));
		reportStaminaSoon();
	}

	private void spendAttackCost(CombatProfile profile, WeaponProfile weapon) {
		if (!profile.usesStamina()) {
			return;
		}

		this.stamina.spend(profile.stamina(), this.staminaScaling.cost(weapon.attackCost()));
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
		// Tested before scaling: a configured zero means "jumping is free for this
		// actor", and no multiplier should be able to resurrect a cost from it.
		if (!profile.usesStamina() || profile.stamina().jumpCost() <= 0) {
			return true;
		}

		boolean paid = this.stamina.spend(profile.stamina(),
				this.staminaScaling.cost(profile.stamina().jumpCost()));
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

		this.stamina.drain(profile.stamina(), this.staminaScaling.cost(cost));

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
	 * Pays for a cast.
	 *
	 * @return false when the caster could not afford it, in which case nothing was
	 *         deducted and the caller must suppress the spell entirely.
	 */
	public boolean spendMana(LivingEntity entity, ManaSettings settings, float cost) {
		if (!ManaPool.spend(entity, settings, this.manaBonusMax, cost)) {
			return false;
		}

		reportManaSoon();
		return true;
	}

	/**
	 * Forces the next mana sync rather than waiting out the throttle.
	 *
	 * <p>Not optional. {@code syncMana} returns early for up to four ticks and only
	 * sends when the value has moved by half a point, so without this a cast would take
	 * a fifth of a second to appear on the bar and would read as the spend not
	 * registering. Two bugs of exactly this shape have already been found in the pool
	 * ceilings, and both were reported as "the bar did not update".
	 */
	private void reportManaSoon() {
		this.manaSyncDelay = 0;
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

		if (!staminaSyncDue(profile)) {
			return;
		}

		this.syncedStamina = this.stamina.current();
		this.syncedStaminaMax = this.stamina.max(profile.stamina());
		this.syncedExhausted = this.stamina.exhausted();
		this.staminaSyncDelay = CombatConstants.STAMINA_SYNC_INTERVAL_TICKS;

		// Two of these are scaled and two are not, which is the whole subtlety of this
		// packet. regenPerSecond and jumpCost are what the client *decides* from — it
		// extrapolates the bar from one and refuses an unaffordable jump from the other
		// — so an unscaled value would have the client disagreeing with the server, and
		// in the jump's case handing out a jump the server then charges full price for.
		//
		// The maximum now moves too, and has to be the pool's own answer rather than the
		// configured figure: attribute points raise the ceiling, and a client told the
		// unbought maximum would draw a bar that reads full while the server was still
		// filling it.
		ServerPlayNetworking.send(player, new StaminaPayload(
				this.stamina.current(),
				this.stamina.max(profile.stamina()),
				Math.round(profile.stamina().regenPerSecond() * this.staminaScaling.regenMultiplier()),
				this.stamina.regenDelay(),
				Math.round(this.staminaScaling.cost(profile.stamina().jumpCost())),
				this.stamina.exhausted()));
	}

	/**
	 * Advances and reports the mana pool.
	 *
	 * <p>Players only: mana is a character resource, and a mob has neither a sheet to
	 * show it on nor anything to spend it on.
	 */
	private void tickMana(LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player)) {
			return;
		}

		StatSettings stats = StatTuning.current();
		ManaSettings settings = stats.mana();

		// Recovery is gated on Arcane, so it is a property of this character rather
		// than of the settings — which is why it is resolved here and threaded through
		// rather than folded into a per-player ManaSettings. Deriving one of those is
		// the trap the stamina scaling exists to avoid: every field is an int, so a
		// slight effect would round away to nothing.
		float regenMultiplier =
				stats.manaRegenMultiplier(StatEffects.statOf(entity, GrandCraftAttributes.ARCANE));

		// Additive, and read fresh every tick rather than cached: a potion can start or
		// run out at any moment, and both edges have to reach the client's bar.
		float bonusPerTick = ManaRegenEffect.bonusPerTick(entity);

		if (settings.enabled()) {
			ManaPool.tick(entity, settings, this.manaBonusMax, regenMultiplier, bonusPerTick);
		}

		// Reported even when mana is switched off, so doing so actually clears the
		// character sheet's row instead of freezing the last value there.
		syncMana(player, settings, regenMultiplier, bonusPerTick);
	}

	/**
	 * Grants mana outright and makes sure the client hears about it promptly — a
	 * potion's instant chunk.
	 *
	 * <p>Public because the mana ceiling lives here: {@code manaBonusMax} is this
	 * controller's business, so anything granting mana has to come through it rather
	 * than reach for {@code ManaPool} with a ceiling it would have to guess at.
	 */
	public void restoreMana(LivingEntity entity, float amount) {
		ManaSettings settings = StatTuning.current().mana();

		if (!settings.enabled() || ManaPool.restore(entity, settings, this.manaBonusMax, amount) <= 0.0F) {
			return;
		}

		// Straight to the front of the sync throttle. A drink is a discrete, deliberate
		// act and the bar jumping a quarter of a second later reads as the potion having
		// missed — the same reason a discrete stamina spend syncs immediately.
		this.manaSyncDelay = 0;
	}

	/**
	 * Tells the owner's client about its mana on the same cadence as stamina.
	 *
	 * <p>With nothing yet able to spend mana this settles at one packet per player
	 * per life, which is also why a fault here would be invisible everywhere except
	 * the character sheet.
	 */
	private void syncMana(ServerPlayer player, ManaSettings settings, float regenMultiplier,
			float bonusPerTick) {
		if (this.manaSyncDelay > 0) {
			this.manaSyncDelay--;
			return;
		}

		// The pool's own ceiling, not the configured one, and it matters twice over:
		// buying a point moves the maximum without moving the current value, so a
		// comparison against the config figure would decide nothing had changed and
		// never send the packet that tells the sheet about the purchase.
		float max = ManaPool.max(settings, this.manaBonusMax);
		float current = ManaPool.current(player, settings, this.manaBonusMax);

		// The *effective* rate, not the configured one. The client extrapolates the bar
		// forward from this figure between packets, so sending the raw value to someone
		// whose mana does not recover would draw a bar climbing on its own and then
		// snapping back on the next sync. Same rule as the scaled jump cost the stamina
		// payload carries.
		//
		// A potion's contribution belongs in it for exactly that reason, and it is the
		// larger term by far while one is running: leave it out and the bar creeps at the
		// Arcane rate and then lurches every fourth tick as the real value lands.
		int regenPerSecond = Math.round(
				settings.regenPerSecond() * regenMultiplier + bonusPerTick * TICKS_PER_SECOND);

		// The rate is part of the test, not just the value and the ceiling — see the
		// note on syncedManaRegen. A reclass moves the rate while the pool sits still.
		if (max == this.syncedManaMax
				&& regenPerSecond == this.syncedManaRegen
				&& Math.abs(current - this.syncedMana) < CombatConstants.STAMINA_SYNC_EPSILON) {
			return;
		}

		this.syncedMana = current;
		this.syncedManaMax = max;
		this.syncedManaRegen = regenPerSecond;
		this.manaSyncDelay = CombatConstants.STAMINA_SYNC_INTERVAL_TICKS;

		// Effective, for the same reason the rate is. The client holds the bar still until
		// this many ticks have passed, so reporting a delay that a potion is currently
		// regenerating straight through would freeze the bar and then jerk it forward.
		int regenDelay = bonusPerTick > 0.0F
				? 0
				: ManaPool.regenDelay(player, settings, this.manaBonusMax);

		ServerPlayNetworking.send(player, new ManaPayload(current, max, regenPerSecond, regenDelay));
	}

	private boolean staminaSyncDue(CombatProfile profile) {
		// A change of state is worth a packet immediately: it changes what the bar
		// means rather than only how full it is, and the client must not keep telling
		// the player they may act when the server has stopped them.
		if (this.stamina.exhausted() != this.syncedExhausted) {
			return true;
		}

		// So is a change of ceiling. Buying stamina does not move the current value, so
		// without this the bar would keep its old length until regen happened to nudge
		// the value — and never at all for an actor whose regen is switched off.
		if (this.stamina.max(profile.stamina()) != this.syncedStaminaMax) {
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

		// Cleared too, so a respawn or an admin's config change lands on the spot
		// rather than on whatever tick a stat next happens to move.
		this.appliedStatSettings = null;
		this.appliedConstitution = Double.NaN;
		this.appliedAgility = Double.NaN;
		this.appliedPoolPoints = null;
		syncStatEffects(entity);
	}

	/**
	 * Expresses this actor's stats as attribute modifiers, and works out what they do
	 * to its stamina.
	 *
	 * <p>Players only — nothing else carries the stat attributes, so for a mob this is
	 * a type check and a return, and its scaling stays neutral.
	 *
	 * <p>Reads the stat values every tick and re-applies only when something moved.
	 * That is two cached attribute reads and three comparisons in the steady state,
	 * and it means a stat gained from gear takes effect the tick it is equipped.
	 */
	private void syncStatEffects(LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player)) {
			return;
		}

		StatSettings settings = StatTuning.current();
		double constitution = StatEffects.statOf(entity, GrandCraftAttributes.CONSTITUTION);
		double agility = StatEffects.statOf(entity, GrandCraftAttributes.AGILITY);
		PoolBlock spentPools = EssenceAwards.progressOf(player).spentPools();

		if (settings == this.appliedStatSettings
				&& constitution == this.appliedConstitution
				&& agility == this.appliedAgility
				&& spentPools.equals(this.appliedPoolPoints)) {
			return;
		}

		this.appliedStatSettings = settings;
		this.appliedConstitution = constitution;
		this.appliedAgility = agility;
		this.appliedPoolPoints = spentPools;
		this.staminaScaling = StaminaScaling.of(entity, settings);

		this.stamina.setBonusMax(settings.poolStaminaBonus(spentPools));

		// Gated on mana being switched on at all. A configured maximum of zero is the
		// feature's off switch, and a bought ceiling must not be a way round it — with
		// mana off the pool is not ticked, so a non-zero ceiling would leave the sheet
		// showing a pool that could never fill.
		//
		// THE RESPAWN TRAP: this method runs on the *new* player during
		// AFTER_RESPAWN, so it must not read or write the MANA attachment. Assigning a
		// controller field is safe because the controller is transient and this one is
		// brand new. If anyone ever folds this figure into ManaState, or marks that
		// attachment copyOnDeath, this line becomes a destructive write to an
		// attachment Fabric may not have copied yet — the exact failure that cost a
		// runtime round for the class attachment and again for the spend record.
		this.manaBonusMax = settings.mana().enabled()
				? settings.poolManaBonus(spentPools)
				: 0;

		StatEffects.apply(entity, settings, spentPools);
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
