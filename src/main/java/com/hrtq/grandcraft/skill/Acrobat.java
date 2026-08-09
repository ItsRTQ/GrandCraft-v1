package com.hrtq.grandcraft.skill;

import com.hrtq.grandcraft.combat.CombatController;
import com.hrtq.grandcraft.combat.CombatProfile;
import com.hrtq.grandcraft.combat.CombatProfiles;
import com.hrtq.grandcraft.combat.GrandCraftCombat;
import com.hrtq.grandcraft.player.GrandCraftAttachments;
import com.hrtq.grandcraft.player.PlayerClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Outlaw's root passive: a second push in mid-air, and walls worth catching.
 *
 * <p><strong>This is the file to edit when Acrobat behaves wrongly.</strong> Who may
 * dash, what counts as a wall, how many grips there are and when they come back are all
 * decided here; the numbers are in {@link SkillSettings}, on
 * {@code /grandcraft config skills}. {@code CombatController} holds the counters, the
 * gravity modifier and the impulse, and never learns what an Outlaw is — the same split
 * {@link CombatMaster} draws.
 *
 * <h2>The shape</h2>
 * <ul>
 *   <li><strong>Jump in mid-air</strong> to dash: upward lift plus a horizontal push
 *       along the way the player is asking to move.</li>
 *   <li><strong>Reach a wall with the dash spent</strong> and the Outlaw grips it,
 *       hanging at zero gravity and getting the dash back.</li>
 *   <li><strong>Jump again from the wall</strong> to dash off it — this one aimed
 *       where the player is <em>looking</em>, not where they are moving.</li>
 *   <li>Two grips, then the ground. Hanging costs stamina, being hit costs more and
 *       drops you, and a swing thrown from a wall lands for less.</li>
 * </ul>
 *
 * <h2>Two counters, and why neither is redundant</h2>
 * {@code airDashCharges} is refilled by a grip, so on its own it bounds nothing at all —
 * a player who keeps finding walls would dash forever. {@code wallHooksLeft} is the only
 * limit, and it is refilled only by standing still on the ground for
 * {@code acrobatGroundResetTicks}. The dash charge, by contrast, comes back on
 * <em>any</em> ground contact, because a player who lands and immediately jumps must not
 * find their move missing — {@code tuning.md} lesson 11 is that a refused input reads as
 * a broken one.
 *
 * <h2>Where the grip ends</h2>
 * Dashing off it, being hit, running the pool dry, landing, dying, entering water, the
 * wall being mined out from under the hands, and — the one that is easy to miss —
 * ceasing to be an Outlaw. A reclass leaves the combat profile perfectly intact, so
 * {@code CombatController}'s own null-profile bail-out never fires for it; {@link #tick}
 * clears the grip on its early return instead, and that is the only thing standing
 * between a mid-air reclass and a permanently weightless player.
 */
public final class Acrobat {
	/**
	 * How far past the hitbox the hands reach for a wall, in blocks.
	 *
	 * <p>Small on purpose. This is the tolerance for "touching", and the failure at a
	 * generous value is gripping thin air near a corner.
	 */
	private static final double GRIP_REACH = 0.10;

	/**
	 * Where the hands are, as fractions of the actor's height — about 0.63 and 1.62
	 * blocks up on a standing player.
	 *
	 * <p><strong>Both must be solid, and that is what makes a wall a wall.</strong> A
	 * single step, a slab, a fence post or a one-block ledge is solid at the lower hand
	 * and air at the upper, so none of them grip. The user's call: a two-block face
	 * reads as something deliberately climbed rather than as scenery the player sticks
	 * to. Two constants to revisit if that turns out too strict.
	 */
	private static final double HAND_LOW = 0.35;
	private static final double HAND_HIGH = 0.90;

	/**
	 * The order an inside corner is resolved in.
	 *
	 * <p>Our own array rather than {@code Direction.Plane.HORIZONTAL}, so that a
	 * Minecraft update cannot quietly change which face a corner picks.
	 *
	 * <p>First match wins, deliberately, rather than anything resembling "nearest".
	 * Nearest is decided by sub-block jitter and would flicker between two answers tick
	 * to tick — {@code tuning.md} lesson 2, the same failure that once made the guard
	 * arc block at close to random. A stable arbitrary choice costs nothing here,
	 * because the stored face is only ever used to re-check that the wall is still
	 * there. It is never the dash direction; that is the look angle.
	 */
	private static final Direction[] FACES = {
			Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	private Acrobat() {
	}

	/**
	 * Advances one actor's air mobility. Called once per server tick from
	 * {@code LivingEntityMixin}, after the controller's own tick.
	 *
	 * <p>After rather than before, for the reason {@code PlayerAttack.tick} is: the
	 * controller has already counted ground contact and already ended a grip that
	 * landed this tick, so everything read here is this tick's truth rather than last
	 * tick's.
	 */
	public static void tick(LivingEntity entity, CombatController controller) {
		if (!(entity instanceof ServerPlayer player)) {
			return;
		}

		SkillSettings settings = SkillTuning.current();

		if (!settings.acrobatEnabled() || !isOutlaw(player)) {
			// The reclass and switched-off cases, and the only place either is caught.
			// Neither reaches the controller's null-profile bail-out, because nothing is
			// wrong with the profile — the player simply stopped being entitled to the
			// grip they are hanging in. Unconditional because clearWallHook is idempotent
			// and costs one lookup on an entity that has no modifier: cheap enough that
			// hiding it behind a flag would only reintroduce the leak.
			controller.clearWallHook(entity);
			return;
		}

		refreshOnGround(controller, settings, entity);

		if (controller.isWallHooked()) {
			// One re-check, on the stored face only. A tick of latency on "someone mined
			// the wall" is invisible; the alternative is eight block reads a tick.
			if (!gripsAt(entity, controller.wallHookFace())) {
				controller.clearWallHook(entity);
			}

			return;
		}

		tryHook(player, controller, settings);
	}

	/**
	 * Hands back what standing on the ground restores.
	 *
	 * <p>The dash on any contact, the hooks only after the full dwell — see the class
	 * notes. The dwell is counted by the controller and zeroes the moment the actor
	 * leaves the ground, so twenty brief touches never add up to a second.
	 */
	private static void refreshOnGround(CombatController controller, SkillSettings settings,
			LivingEntity entity) {
		if (!entity.onGround()) {
			return;
		}

		controller.setAirDashCharges(1);

		if (controller.groundedTicks() >= settings.acrobatGroundResetTicks()) {
			controller.setWallHooksLeft(settings.acrobatWallHooks());
		}
	}

	/**
	 * Looks for a wall to catch, and catches it.
	 *
	 * <p>Every clause before the block reads is a field compare, and the class lookup is
	 * last because it is the dearest of them. In steady state the whole method costs one
	 * {@code instanceof} for every living entity in the world, which
	 * {@code LivingEntityMixin} already pays; the eight block reads are reached only by
	 * an airborne Outlaw who has spent a dash and has a grip left.
	 */
	private static void tryHook(ServerPlayer player, CombatController controller,
			SkillSettings settings) {
		if (!settings.acrobatHooksEnabled()
				|| player.onGround()
				// A grip is what the dash buys. Without this an Outlaw sticks to the
				// first wall they walk off a ledge beside, which is not the move.
				|| controller.airDashCharges() > 0
				|| controller.wallHooksLeft() <= 0
				|| controller.wallHookCooldown() > 0
				|| controller.staminaExhausted()) {
			return;
		}

		Direction face = wallBeside(player);

		if (face == null) {
			return;
		}

		controller.setWallHooksLeft(controller.wallHooksLeft() - 1);
		controller.setAirDashCharges(1);
		controller.grantWallHook(player, face,
				settings.acrobatHangDrainPerTick(), settings.acrobatHookCooldownTicks());
	}

	/**
	 * Answers a dash request. Called from the payload receiver.
	 *
	 * @param travelDirection the horizontal direction the client asked for; may be zero,
	 *                        meaning the player was giving no movement input
	 * @return false when the dash was refused, in which case nothing was spent and —
	 *         standing policy — nothing is sent back
	 */
	public static boolean requestDash(ServerPlayer player, Vec3 travelDirection) {
		CombatProfile profile = CombatProfiles.forEntity(player);

		if (profile == null) {
			return false;
		}

		SkillSettings settings = SkillTuning.current();

		if (!settings.acrobatEnabled() || !isOutlaw(player)) {
			return false;
		}

		CombatController controller = GrandCraftCombat.controllerOf(player);

		if (player.onGround() || controller.airDashCharges() <= 0) {
			return false;
		}

		// The dodge's own rule, through the shared accessor: you may not bail out of a
		// swing you have committed to. An Outlaw is the most mobile class in the game and
		// is still not exempt from the mod's one universal rule about attacks.
		if (!controller.canActFreely()) {
			return false;
		}

		if (!controller.spendSkillCost(profile, settings.acrobatDashCost())) {
			return false;
		}

		// Off a wall the look angle wins, and it is read here rather than sent, so a
		// client that has never been told it is hooked cannot pick the wrong rule.
		Vec3 direction = controller.isWallHooked() ? player.getLookAngle() : travelDirection;

		if (!isUsable(direction)) {
			direction = player.getLookAngle();
		}

		controller.setAirDashCharges(controller.airDashCharges() - 1);

		// Before the impulse: releasing stamps the hook cooldown, and the grip must be
		// gone before the actor is thrown away from the wall it was on.
		controller.clearWallHook(player);

		CombatController.airDash(player, direction,
				settings.acrobatDashSpeedPerTick(), settings.acrobatDashLiftPerTick());
		return true;
	}

	/**
	 * Called for every hit that actually landed on an actor.
	 *
	 * <p>Lives in the damage listener rather than inside {@code applyStagger}, and the
	 * distinction is the one {@code breakGuard} documents: the stagger tracker suppresses
	 * the third consecutive reaction so that pressure cannot stunlock, which is right for
	 * staggering and wrong here. Being knocked off a wall has to happen every time.
	 *
	 * <p>An absorbed hit never reaches this at all — {@code ALLOW_DAMAGE} returns false
	 * for one, so a hooked Outlaw behind a raised guard keeps the wall. That is emergent
	 * rather than designed, and it is good: it is the one way to stay up under fire.
	 */
	public static void onHit(LivingEntity entity, CombatController controller) {
		if (!controller.isWallHooked()) {
			return;
		}

		CombatProfile profile = CombatProfiles.forEntity(entity);

		if (profile != null) {
			controller.drainSkillCost(profile, SkillTuning.current().acrobatHitStaminaCost());
		}

		controller.clearWallHook(entity);
	}

	/**
	 * What a swing is worth right now, or exactly {@code 1.0} when nothing applies.
	 *
	 * <p>Three consequences of applying this where it is applied, all of which would
	 * otherwise be found as bugs:
	 *
	 * <ul>
	 *   <li><strong>It weakens the drain on a defender's guard too.</strong>
	 *       {@code absorbBlockedHit} charges stamina in proportion to the damage
	 *       absorbed, so an Outlaw on a wall is a quarter worse at opening a turtle up
	 *       as well as a quarter worse at killing. Coherent — a wall is a bad place to
	 *       fight from — and the mirror of the note on Combat Master's multiplier.</li>
	 *   <li><strong>Enchantment damage is not reduced.</strong> That term goes through a
	 *       separate redirect which does not see this multiplier, exactly as it does not
	 *       see Combat Master's. Consistent rather than leaky.</li>
	 *   <li><strong>{@code MeleeDamage.scalingFor} is untouched.</strong> The client's
	 *       weapon tooltip calls that same method, and folding a transient state into it
	 *       would make the tooltip's number change when the player brushed a wall.</li>
	 * </ul>
	 *
	 * <p>Not re-checked against the class, for the reason {@code CombatMaster} gives at
	 * the same seam: only an Outlaw can be hooked, and the one way to hold a grip while
	 * being something else is to reclass in mid-air — which {@link #tick} clears on the
	 * next tick and is not worth a branch on every swing in the game.
	 */
	public static float swingScale(Player attacker) {
		if (!(attacker instanceof ServerPlayer player)) {
			return 1.0F;
		}

		// Nullable read rather than controllerOf: a swing must not be the thing that
		// allocates a controller for an actor that has never fought.
		CombatController controller = player.getAttached(GrandCraftAttachments.COMBAT_CONTROLLER);

		return controller != null && controller.isWallHooked()
				? SkillTuning.current().acrobatHangDamageScale()
				: 1.0F;
	}

	/**
	 * The face of a wall the actor is against, or null.
	 *
	 * <p>Probed out from the <strong>bounding box</strong>, never from
	 * {@code blockPosition()}. That is the block containing the actor's centre, so an
	 * actor standing at the far edge of it is over a block clear of the wall on the other
	 * side and the naive {@code blockPosition().relative(face)} check still passes — the
	 * bug reads as "it hooks onto walls I am not touching".
	 */
	private static Direction wallBeside(LivingEntity entity) {
		for (Direction face : FACES) {
			if (gripsAt(entity, face)) {
				return face;
			}
		}

		return null;
	}

	/** Whether there is a two-block-tall grip on this side of the actor. */
	private static boolean gripsAt(LivingEntity entity, Direction face) {
		if (face == null) {
			return false;
		}

		AABB box = entity.getBoundingBox();
		double centreX = (box.minX + box.maxX) * 0.5;
		double centreZ = (box.minZ + box.maxZ) * 0.5;
		double reachX = (box.maxX - box.minX) * 0.5 + GRIP_REACH;
		double reachZ = (box.maxZ - box.minZ) * 0.5 + GRIP_REACH;

		double x = centreX + face.getStepX() * reachX;
		double z = centreZ + face.getStepZ() * reachZ;
		double height = entity.getBbHeight();

		return sturdyAt(entity.level(), x, box.minY + HAND_LOW * height, z, face)
				&& sturdyAt(entity.level(), x, box.minY + HAND_HIGH * height, z, face);
	}

	/**
	 * Whether the block at this point offers a face worth gripping.
	 *
	 * <p>{@code face.getOpposite()} because the surface the hands are on is the block's
	 * face pointing back at the actor, not the direction the actor reached in.
	 */
	private static boolean sturdyAt(Level level, double x, double y, double z, Direction face) {
		BlockPos pos = BlockPos.containing(x, y, z);
		return level.getBlockState(pos).isFaceSturdy(level, pos, face.getOpposite());
	}

	/** Copied from the dodge receiver: a direction too short to normalise is no answer. */
	private static boolean isUsable(Vec3 direction) {
		double lengthSqr = direction.x * direction.x + direction.z * direction.z;
		return Double.isFinite(lengthSqr) && lengthSqr > 1.0E-4;
	}

	private static boolean isOutlaw(ServerPlayer player) {
		PlayerClass playerClass = player.getAttachedOrElse(
				GrandCraftAttachments.PLAYER_CLASS, PlayerClass.PEASANT);

		return ClassPassive.forRoot(playerClass) == ClassPassive.ACROBAT;
	}
}
