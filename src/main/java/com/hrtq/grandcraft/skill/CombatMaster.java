package com.hrtq.grandcraft.skill;

import com.hrtq.grandcraft.combat.CombatController;
import com.hrtq.grandcraft.combat.GrandCraftCombat;
import com.hrtq.grandcraft.network.CombatMasterPayload;
import com.hrtq.grandcraft.player.GrandCraftAttachments;
import com.hrtq.grandcraft.player.PlayerClass;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The Warrior's root passive: block well, then hit harder.
 *
 * <p><strong>This is the file to edit when Combat Master behaves wrongly.</strong> Who
 * gets a window, what opens one, what closes one and what an empowered blow is worth
 * are all decided here; the numbers themselves are in {@link SkillSettings}, on
 * {@code /grandcraft config skills}.
 *
 * <h2>The shape</h2>
 * <ul>
 *   <li><strong>Opened by a clean block</strong> — the guard absorbed a hit <em>and
 *       survived it</em>. The hit that empties stamina and breaks the guard earns
 *       nothing, which is what keeps a guard break the punishment that block was tuned
 *       around rather than a consolation prize.</li>
 *   <li><strong>Closed by any melee swing that reaches a target</strong>, whether or
 *       not it took health off — and by the window running out otherwise.</li>
 *   <li>While open: a movement bonus, and one blow at {@code combatMasterDamageScale}.</li>
 * </ul>
 *
 * <h2>Why "reaches a target" and not "deals damage"</h2>
 * The user's call, and it has a consequence that is the point rather than a side
 * effect: {@code CombatController.absorbBlockedHit} charges a blocking defender stamina
 * <em>in proportion to the damage absorbed</em>. So an empowered blow into a raised
 * guard drains it half again as fast. Spending the window on an opponent who blocked it
 * is therefore not a waste — it is how a Warrior breaks a turtle open.
 *
 * <h2>Two packets, and none for expiry</h2>
 * The client is told when a window opens, with the resolved duration, and when one
 * closes <em>early</em>. Nothing is sent when a window simply runs out, because the
 * client already knows when that is — it was told the length. That is why the countdown
 * on screen needs no per-tick sync and cannot drift: it is not extrapolated from a rate,
 * it is a deadline.
 */
public final class CombatMaster {
	private CombatMaster() {
	}

	/**
	 * Called when a guard absorbed a hit and is still up.
	 *
	 * <p>Takes the entity rather than a player because that is what the damage listener
	 * has; everything that is not a Warrior falls straight back out. Mobs can guard too,
	 * and none of them have a class.
	 */
	public static void onCleanBlock(LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player) || !isWarrior(player)) {
			return;
		}

		SkillSettings settings = SkillTuning.current();

		// A window of zero is the feature's off switch, and the gate has to be here
		// rather than at the modifier: granting a zero-length window would still send a
		// packet and flash the badge for a frame.
		if (!settings.combatMasterEnabled()) {
			return;
		}

		int ticks = settings.combatMasterWindowTicks();

		GrandCraftCombat.controllerOf(player).grantEmpowerment(
				player, ticks, settings.combatMasterSpeedFraction());

		// The effective duration, never the configured one — the client draws a
		// countdown from it, and tuning.md lesson 13 is that a client given a raw
		// setting draws something the server is not doing.
		ServerPlayNetworking.send(player, new CombatMasterPayload(ticks));
	}

	/**
	 * Called for a melee swing that reached a target. Returns what to multiply the
	 * attacker's damage by, and closes the window if it was open.
	 *
	 * <p>Reading and consuming in one call on purpose: they are the same event, and two
	 * methods would be two chances for a swing to be empowered without spending the
	 * window or to spend it without being empowered.
	 *
	 * @return the damage multiplier, or exactly {@code 1.0} when nothing was open
	 */
	public static float empowerSwing(Player attacker) {
		if (!(attacker instanceof ServerPlayer player)) {
			return 1.0F;
		}

		CombatController controller = GrandCraftCombat.controllerOf(player);

		if (!controller.isEmpowered()) {
			return 1.0F;
		}

		controller.clearEmpowerment(player);
		ServerPlayNetworking.send(player, new CombatMasterPayload(0));

		// Deliberately not re-checked against the class. A window can only ever have
		// been granted to a Warrior, and the one way to hold one while being something
		// else is to be reclassed inside five seconds — which costs a single hit and is
		// not worth a branch on every swing in the game.
		return SkillTuning.current().combatMasterDamageScale();
	}

	private static boolean isWarrior(ServerPlayer player) {
		PlayerClass playerClass = player.getAttachedOrElse(
				GrandCraftAttachments.PLAYER_CLASS, PlayerClass.PEASANT);

		return ClassPassive.forRoot(playerClass) == ClassPassive.COMBAT_MASTER;
	}
}
