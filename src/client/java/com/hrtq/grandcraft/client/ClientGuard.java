package com.hrtq.grandcraft.client;

import com.hrtq.grandcraft.combat.CombatConstants;
import com.hrtq.grandcraft.combat.CombatController;
import com.hrtq.grandcraft.network.GuardPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;

/**
 * Turns holding the use key into a guard request.
 *
 * <p>Deliberately thin, like {@link ClientDodge}. The client reports only that the
 * key is down; whether that becomes a guard, how long it takes to raise, what
 * holding it costs and what an absorbed hit costs are all the server's, and the
 * guard comes back as an ordinary phase packet like any other actor's.
 *
 * <h2>Sharing the right button with vanilla</h2>
 * The use key already opens chests, toggles doors, places off-hand blocks and raises
 * vanilla shields, so the guard cannot simply take it. The split is by edge:
 *
 * <ul>
 *   <li><strong>The press belongs to vanilla.</strong> {@link #holding} is latched at
 *       the end of the tick, and {@code Minecraft.handleKeybinds} has already run by
 *       then — so the first right-click of any hold does whatever it always did.
 *       That is what keeps chests, doors and interactions working with a sword in
 *       hand.</li>
 *   <li><strong>The hold belongs to the guard.</strong> From the next tick on,
 *       {@link #suppressesItemUse()} cancels vanilla's repeat, so a held button
 *       cannot keep placing blocks or re-triggering an interaction from behind a
 *       raised guard.</li>
 * </ul>
 *
 * <p>A shield is the one case where vanilla's press does something that has to be
 * undone: it starts <em>using</em> the shield, which would leave vanilla's own block
 * pose and slowdown running underneath ours. {@link #cancelVanillaUse} releases it
 * the moment the guard engages. The shield keeps its meaning — the server reads the
 * off-hand itself and charges less for absorbing with one.
 *
 * <p>Suppression is keyed on the client's own intent rather than on the guard the
 * server has confirmed, so it cannot flicker with latency. The cost is that holding
 * the key while the server is refusing the guard — exhausted, or mid-recovery —
 * also suppresses ordinary use until the button is released.
 */
public final class ClientGuard {
	/** Whether the server has been told the key is down and not yet that it is up. */
	private static boolean holding;

	/** Ticks until the hold is re-asserted. */
	private static int keepalive;

	private ClientGuard() {
	}

	/** Sends the edges of a hold, and re-asserts it while it lasts. */
	public static void tick(Minecraft client) {
		if (!wantsGuard(client)) {
			if (holding) {
				holding = false;
				keepalive = 0;
				ClientPlayNetworking.send(new GuardPayload(false));
			}

			return;
		}

		if (!holding) {
			holding = true;
			keepalive = CombatConstants.GUARD_KEEPALIVE_TICKS;
			ClientPlayNetworking.send(new GuardPayload(true));
			cancelVanillaUse(client);
			return;
		}

		// Re-asserted rather than sent once, so the server can forget a hold it has
		// stopped hearing about instead of leaving a guard standing after a disconnect.
		// This is also what raises the guard again by itself once a stagger or an empty
		// pool stops refusing it, with no second press.
		if (--keepalive <= 0) {
			keepalive = CombatConstants.GUARD_KEEPALIVE_TICKS;
			ClientPlayNetworking.send(new GuardPayload(true));
		}
	}

	/**
	 * Whether vanilla's right-click use is currently the guard's rather than its own.
	 *
	 * <p>Reads the latched hold, never the live predicate: the whole point is that the
	 * tick which starts a hold has already let vanilla have its press.
	 */
	public static boolean suppressesItemUse() {
		return holding;
	}

	public static void clear() {
		holding = false;
		keepalive = 0;
	}

	/**
	 * Whether the player is asking to guard right now.
	 *
	 * <p>The server re-checks every one of these; refusing here only keeps a held key
	 * from filling the channel with requests that would be dropped.
	 */
	private static boolean wantsGuard(Minecraft client) {
		LocalPlayer player = client.player;

		// 26.2 moved the current screen off Minecraft and onto Gui.
		if (player == null || client.gui.screen() != null || player.isSpectator()) {
			return false;
		}

		if (!client.options.keyUse.isDown()) {
			return false;
		}

		// Something else already owns the button: drawing a bow, charging a trident,
		// eating. A shield is the exception — vanilla "using" one is the same gesture
		// as guarding with it, and cancelVanillaUse takes it back a moment later.
		if (player.isUsingItem() && !player.getUseItem().has(DataComponents.BLOCKS_ATTACKS)) {
			return false;
		}

		// Tags are synced to the client, so this is the same answer the server reaches.

		return CombatController.hasGuardImplement(player);
	}

	/**
	 * Takes back the item use vanilla started on the press.
	 *
	 * <p>Only ever has anything to do for a shield, since nothing else that can guard
	 * has a use action. Without it vanilla's block pose and its use-slowdown would run
	 * underneath GrandCraft's guard, and the two would disagree about what the player
	 * is doing.
	 */
	private static void cancelVanillaUse(Minecraft client) {
		LocalPlayer player = client.player;

		if (player != null && player.isUsingItem() && client.gameMode != null) {
			client.gameMode.releaseUsingItem(player);
		}
	}
}
