package com.hrtq.grandcraft.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Resolves a player's swing when its wind-up ends.
 *
 * <p><strong>This is the file to edit when the player's blow lands at the wrong moment or
 * on the wrong thing.</strong> {@link CombatController} owns the phases and the stamina;
 * {@code Weapons} owns how long each phase is; this owns the one question neither of them
 * should answer, which is what a swing connects with.
 *
 * <h2>The click starts a wind-up; it decides nothing else</h2>
 *
 * A left click books a wind-up and <em>nothing more</em> — no target, no reach check, no
 * damage. When the wind-up ends the swing looks for what is in front of the player right
 * then, and hits it.
 *
 * <p>That is the whole shape, and it is deliberate on two counts. It means <strong>a swing
 * at empty air is a real swing</strong>, with the same telegraph and the same cost as one
 * aimed at something, so the animation plays whenever the player attacks rather than only
 * when they happened to click on a mob. And it means the hit is decided at the moment the
 * weapon arrives, which is the moment the animation shows it arriving.
 *
 * <p>An earlier version banked the target from the click and re-checked its range at the
 * active frame. It made the two entry paths behave differently — clicking a mob wound up,
 * clicking air did not — which is what {@code "it happens weirdly when the player hits a
 * target"} was describing.
 *
 * <h2>How the target is found</h2>
 *
 * {@code ProjectileUtil.getHitResultOnViewVector}, along the player's view for their own
 * {@code entityInteractionRange}. It clips against {@code ClipContext.Block.COLLIDER}, so a
 * swing cannot reach through a wall, and it is the server's own view of where the player is
 * looking rather than anything the client asserted.
 *
 * <p>Then {@code Player.isWithinAttackRange}, vanilla's own reach rule, so the weapon's
 * reach attribute keeps meaning what it means everywhere else — the same check
 * {@code ServerGamePacketListenerImpl} makes on an attack packet. (It is declared on
 * {@code Player}, not {@code ServerPlayer} — the project's own note said otherwise until
 * this was read out of the jar.)
 *
 * <h2>The damage math does not move — only when it happens</h2>
 *
 * Landing the hit is {@code Player.attack(target)}, vanilla's own method, so the swing still
 * runs through {@code PlayerAttackMixin}'s redirects and comes out with the same stat-driven
 * damage it had when it was dealt on the click. This moved the clock, not the arithmetic,
 * and keeping it one call is what guarantees that.
 */
public final class PlayerAttack {
	/**
	 * The padding {@code isWithinAttackRange} is given around the weapon's own reach —
	 * vanilla's own figure, passed on unchanged.
	 */
	private static final double RANGE_PADDING = 3.0;

	/**
	 * The player whose blow is being landed right now, if any.
	 *
	 * <p><strong>Landing a blow re-enters the attack event.</strong> Fabric fires
	 * {@code AttackEntityCallback} from the HEAD of {@code Player.attack}, which is the
	 * method this class calls to deal the damage — so without a guard the swing arrives
	 * back at {@code GrandCraftCombat}'s listener, is refused for being mid-swing, and is
	 * cancelled. The failure would be silent and total: a wind-up that never hits.
	 *
	 * <p>A field rather than a boolean so it names <em>which</em> player, and a plain
	 * static rather than a {@code ThreadLocal} because both the write here and the read in
	 * the listener happen on the server thread — the listener returns before consulting
	 * this on any client one.
	 */
	private static Player landingBlow;

	private PlayerAttack() {
	}

	/** Whether this player's own delayed blow is what is currently running. */
	public static boolean isLandingBlow(Player player) {
		return landingBlow == player;
	}

	/**
	 * Lands the blow if this is the frame for it. Called once per player tick, after the
	 * controller has advanced — see {@code LivingEntityMixin}.
	 *
	 * <p>Ordering matters and is why this is not inside {@code CombatController.tick}: the
	 * controller has to have already moved into {@code ATTACK_ACTIVE} this tick for
	 * {@code canDealDamage} to be true, so this must run after it, not within it.
	 */
	public static void tick(LivingEntity entity, CombatController controller) {
		if (!(entity instanceof ServerPlayer player) || !controller.canDealDamage()) {
			return;
		}

		Entity target = targetInFront(player);

		if (target == null) {
			// Nothing there this tick, and deliberately not spent. The hit window is what
			// a committed swing is paid in — someone who steps into reach before it closes
			// is still hit, and the swing only truly misses when the window runs out with
			// nobody in front of it. Same rule MeleeAttackGoalMixin follows for a mob:
			// only spend the swing's one hit when it is going to connect.
			return;
		}

		// Spent before the blow, not after: Player.attack runs a great deal of other
		// people's code, and a swing that somehow re-entered here would otherwise get a
		// second hit out of one window.
		controller.consumeActiveHit();

		// try/finally, not a bare pair of assignments: Player.attack runs enchantments,
		// death handling and any listener the rest of the game has hung off a damage
		// event, and a throw from any of them would otherwise leave the guard raised and
		// silently disarm every future swing this player made.
		landingBlow = player;

		try {
			player.attack(target);
		} finally {
			landingBlow = null;
		}
	}

	/**
	 * What this swing connects with, or null for a whiff.
	 *
	 * <p>Resolved fresh at the active frame rather than remembered from the click, which is
	 * the whole point — see the class notes.
	 */
	private static Entity targetInFront(ServerPlayer player) {
		double reach = player.entityInteractionRange();

		HitResult hit = ProjectileUtil.getHitResultOnViewVector(player,
				candidate -> candidate != player && candidate.isPickable()
						&& !candidate.isSpectator(),
				reach);

		if (!(hit instanceof EntityHitResult entityHit)) {
			// A block hit or a clean miss. Both are whiffs, and a block hit means the ray
			// stopped at a wall — so the mob behind it is safe, which is what
			// ClipContext.Block.COLLIDER is there for.
			return null;
		}

		Entity target = entityHit.getEntity();

		if (!target.isAlive()) {
			return null;
		}

		// Vanilla's own reach rule on top of the ray, because the ray is bounded by the
		// player's interaction range while this accounts for the weapon in their hand.
		return player.isWithinAttackRange(player.getMainHandItem(),
				target.getBoundingBox(), RANGE_PADDING) ? target : null;
	}
}
