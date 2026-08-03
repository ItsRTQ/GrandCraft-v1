package com.hrtq.grandcraft.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

/**
 * The radial quick-slot wheel that replaces the vanilla hotbar: what it is aimed at,
 * and what selecting means.
 *
 * <p><strong>This is the file to edit when the wheel is the wrong shape or picks the
 * wrong slot.</strong> {@link RadialMenuElement} decides only how it looks, the same
 * way {@link HudBars} owns the bar column's geometry and the three bar elements own
 * only what they draw.
 *
 * <h2>Twelve wedges, eleven of them items</h2>
 *
 * Wedge 0 is at the top and they run clockwise, so the bottom wedge is number six —
 * that one is the inventory button and every other one is an inventory slot.
 * {@link #SLOT_FOR_WEDGE} is the whole layout contract: nothing else in the mod may
 * turn an angle into a slot, because two places doing that arithmetic is two places
 * that can disagree about which item the player just picked.
 *
 * <p>The array already names all eleven slots, but {@link #slotForWedge} refuses any
 * beyond {@link Inventory#getSelectionSize()}. Vanilla's is nine, so the last two
 * wedges draw locked until that limit is raised; they then unlock with no change
 * here. That is deliberate — it keeps "what the wheel offers" and "what the player is
 * allowed to hold" as one answer rather than two constants to keep in step, and means
 * a wedge can never ask {@code setSelectedSlot} for a slot it throws on.
 *
 * <h2>Aimed, not pointed at</h2>
 *
 * There is no cursor. Mouse movement is accumulated into a vector from the centre
 * while the key is held — see {@code MouseHandlerRadialMixin}, which takes those
 * deltas away from the camera — and the wedge is whichever direction that vector
 * points. Inside {@link #DEAD_ZONE} it points nowhere and releasing changes nothing,
 * so a player who taps the key without moving the mouse keeps the item they had. That
 * matters more than it sounds: the alternative is a tap silently selecting wedge 0.
 */
public final class RadialMenu {
	/** Twelve positions: eleven slots and the inventory button. */
	public static final int WEDGES = 12;

	/** The bottom wedge. Opens the player inventory instead of selecting an item. */
	public static final int INVENTORY_WEDGE = 6;

	/** Nothing aimed at: inside the dead zone, or the wheel is shut. */
	public static final int NO_WEDGE = -1;

	/** One wedge, in degrees. Twelve of them make the circle. */
	private static final double WEDGE_DEGREES = 360.0 / WEDGES;

	/**
	 * Wedge → inventory slot, clockwise from the top, with the inventory button at
	 * six. The one place an angle becomes a slot.
	 */
	private static final int[] SLOT_FOR_WEDGE = {
			0, 1, 2, 3, 4, 5,
			NO_WEDGE,
			6, 7, 8, 9, 10};

	/**
	 * Raw mouse pixels to aim units. <strong>Lower this if the wheel still feels
	 * twitchy</strong> — it is the one value that decides how far the hand must
	 * travel for a given swing, and it is the first knob to reach for.
	 *
	 * <p>Deliberately not scaled by the player's mouse-sensitivity option. That option
	 * is tuned for aiming a crosshair at something across a room, which is a much finer
	 * task than choosing one of twelve directions, and inheriting it would make the
	 * wheel worst for exactly the players who set sensitivity highest.
	 */
	private static final double AIM_SENSITIVITY = 0.4;

	/**
	 * How far the aim must leave the centre before it selects anything, in aim units.
	 * <strong>This is what stops a nudge selecting something</strong>, and coming back
	 * inside it is how a player who changed their mind cancels.
	 */
	private static final double DEAD_ZONE = 22.0;

	/**
	 * The aim vector is clamped to this length. It is the wheel's own unit rather than
	 * a pixel count — the element scales it to the drawn radius through
	 * {@link #aimFractionX()}. <strong>Raise this if neighbouring slots are hard to
	 * tell apart.</strong>
	 *
	 * <p>It matters more than it looks. The angle a given mouse movement sweeps is
	 * proportional to the movement divided by the vector's current length, so a short
	 * clamp makes every wedge boundary a hair trigger: at the old 48 units, a flick
	 * worth 30 crossed a whole wedge. The clamp is still needed — without one, a long
	 * sweep banks distance that has to be unwound before the wheel answers the other
	 * way, which reads as the wheel sticking.
	 */
	private static final double MAX_AIM = 110.0;

	/**
	 * How far past a boundary the aim must go before the selection follows it, in
	 * degrees. <strong>Raise this if the highlight flickers between two slots.</strong>
	 *
	 * <p>A wedge is 30 degrees, so its centre is 15 away from either boundary: at 6,
	 * switching means getting within 9 degrees of the new wedge's centre. Without this
	 * an aim resting on a boundary alternates between two slots every frame, and the
	 * player cannot tell which one a release will take.
	 */
	private static final double SWITCH_MARGIN_DEGREES = 6.0;

	/** How long the wheel takes to fade in. Closing is immediate — see the element. */
	private static final long FADE_MILLIS = 90L;

	private static boolean open;
	private static long openedAtMillis;
	private static double aimX;
	private static double aimY;
	private static int highlighted = NO_WEDGE;

	/** The key's physical state last tick. Tracked through screens — see {@link #tick}. */
	private static boolean keyWasHeld;

	private RadialMenu() {
	}

	public static boolean isOpen() {
		return open;
	}

	public static int highlightedWedge() {
		return highlighted;
	}

	/**
	 * The inventory slot a wedge selects, or {@link #NO_WEDGE} if it selects nothing —
	 * the inventory button, or a slot past what the player may actually hold.
	 */
	public static int slotForWedge(int wedge) {
		if (wedge < 0 || wedge >= WEDGES) {
			return NO_WEDGE;
		}

		int slot = SLOT_FOR_WEDGE[wedge];
		return slot >= 0 && slot < Inventory.getSelectionSize() ? slot : NO_WEDGE;
	}

	/** How far into the fade the wheel is, 0 to 1. */
	public static float openFraction(long nowMillis) {
		if (!open) {
			return 0.0F;
		}

		return Math.clamp((nowMillis - openedAtMillis) / (float) FADE_MILLIS, 0.0F, 1.0F);
	}

	/**
	 * Drives the wheel, once per client tick. Pressing opens it, letting go commits it —
	 * it has to be up while the player aims, and the aim is the whole interaction.
	 *
	 * <p><strong>{@code held} must be the physical state of the key, not
	 * {@code KeyMapping.isDown} and not a click count.</strong> Both of those lie in
	 * exactly the case this has to survive, and the wheel's own inventory button is
	 * what walks into it — the player is still holding the key when the inventory
	 * appears. Vanilla releases every mapping when a screen opens and does not
	 * re-assert them until the next GLFW key-<em>repeat</em>, so a mapping-based
	 * detector sees the key go up and come back down while the finger never moved, and
	 * reopens the wheel over whatever the player just did. Clicks are no better:
	 * repeats call {@code KeyMapping.click} too, so a held key manufactures presses
	 * once the repeat delay elapses.
	 *
	 * <p>The physical state has neither problem, and this keeps tracking it
	 * <em>while a screen is up</em>. That is what makes closing the inventory with the
	 * key still held do nothing at all: there is no rising edge, because as far as this
	 * is concerned the key never came up.
	 */
	public static void tick(Minecraft client, boolean held) {
		boolean wasHeld = keyWasHeld;
		keyWasHeld = held;

		// No wheel over a screen. It cannot be aimed there — the mouse belongs to the
		// screen — and the inventory it opens is exactly what would be covered.
		if (client.gui.screen() != null) {
			close();
			return;
		}

		if (held && !wasHeld) {
			openWheel(client);
		} else if (!held && wasHeld) {
			commit(client);
		}
	}

	/** Takes mouse movement the camera did not get. */
	public static void aim(double dx, double dy) {
		if (!open) {
			return;
		}

		aimX += dx * AIM_SENSITIVITY;
		aimY += dy * AIM_SENSITIVITY;

		double length = Math.sqrt(aimX * aimX + aimY * aimY);

		if (length > MAX_AIM) {
			aimX = aimX / length * MAX_AIM;
			aimY = aimY / length * MAX_AIM;
			length = MAX_AIM;
		}

		if (length < DEAD_ZONE) {
			highlighted = NO_WEDGE;
			return;
		}

		// Measured from straight up and running clockwise, which is how the wedges are
		// numbered: x is the sine term and y is negated because screen y grows downward.
		double degrees = Math.toDegrees(Math.atan2(aimX, -aimY));
		int candidate = (int) Math.round(degrees / WEDGE_DEGREES + WEDGES) % WEDGES;

		if (candidate == highlighted) {
			return;
		}

		// Changing the answer costs more than keeping it: the aim has to be properly
		// inside the new wedge, not merely over the line. Only from a standing start
		// does the nearest wedge win outright.
		if (highlighted != NO_WEDGE
				&& offsetFromCentre(degrees, candidate) > WEDGE_DEGREES / 2.0 - SWITCH_MARGIN_DEGREES) {
			return;
		}

		highlighted = candidate;
	}

	/** How far the aim is from a wedge's centre line, in degrees, always positive. */
	private static double offsetFromCentre(double degrees, int wedge) {
		double difference = Math.abs(degrees - wedge * WEDGE_DEGREES) % 360.0;
		return difference > 180.0 ? 360.0 - difference : difference;
	}

	/** Where the aim sits, as a fraction of the ring's radius on each axis. */
	public static double aimFractionX() {
		return aimX / MAX_AIM;
	}

	public static double aimFractionY() {
		return aimY / MAX_AIM;
	}

	/** Whether the aim is far enough out to mean anything. */
	public static boolean aimIsLive() {
		return Math.sqrt(aimX * aimX + aimY * aimY) >= DEAD_ZONE;
	}

	/**
	 * Acts on whatever is aimed at and shuts the wheel. Called on key release and by
	 * a left-click while it is open, which is why it is safe to call when nothing is
	 * highlighted — that is a player who changed their mind.
	 */
	public static void commit(Minecraft client) {
		if (!open) {
			return;
		}

		int wedge = highlighted;
		close();

		LocalPlayer player = client.player;

		if (player == null || wedge == NO_WEDGE) {
			return;
		}

		if (wedge == INVENTORY_WEDGE) {
			openInventory(client, player);
			return;
		}

		int slot = slotForWedge(wedge);

		if (slot != NO_WEDGE) {
			// No packet of ours: MultiPlayerGameMode.ensureHasSentCarriedItem notices
			// the changed slot during its own tick and sends vanilla's carried-item
			// packet, exactly as it does for the scroll wheel.
			player.getInventory().setSelectedSlot(slot);
		}
	}

	public static void close() {
		open = false;
		highlighted = NO_WEDGE;
		aimX = 0.0;
		aimY = 0.0;
	}

	/** Between worlds: no wheel left standing, and no key edge carried across. */
	public static void clear() {
		close();
		keyWasHeld = false;
	}

	private static void openWheel(Minecraft client) {
		if (client.player == null) {
			return;
		}

		open = true;
		openedAtMillis = Util.getMillis();
		highlighted = NO_WEDGE;
		aimX = 0.0;
		aimY = 0.0;
	}

	/**
	 * Vanilla's own two ways of opening the inventory, from the branch of
	 * {@code Minecraft.handleKeybinds} this wheel replaces. A server that controls the
	 * inventory has to be asked for it rather than shown a local screen.
	 */
	private static void openInventory(Minecraft client, LocalPlayer player) {
		if (client.gameMode != null && client.gameMode.isServerControlledInventory()) {
			player.sendOpenInventory();
		} else {
			client.gui.setScreen(new InventoryScreen(player));
		}
	}
}
