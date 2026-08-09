package com.hrtq.grandcraft.client;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.client.gui.GrandCraftScreen;
import com.hrtq.grandcraft.client.hud.RadialMenu;
import com.hrtq.grandcraft.network.UseSkillPayload;
import com.hrtq.grandcraft.skill.SkillLoadout;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class GrandCraftKeyMappings {
	public static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(GrandCraft.id("main"));

	public static final KeyMapping OPEN_MENU = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.grandcraft.open_menu",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_EQUAL,
			CATEGORY));

	/**
	 * Dodge is a dedicated key rather than a double-tap or a modifier on an existing
	 * one. A defensive verb has to be committable deliberately and instantly; a
	 * double-tap costs a whole input window to recognise, which is most of a dodge.
	 */
	public static final KeyMapping DODGE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.grandcraft.dodge",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_V,
			CATEGORY));

	/**
	 * The quick-slot wheel, on the key that used to open the inventory — because the
	 * wheel is now how the inventory is reached, so the key still means what the
	 * player already believes it means.
	 *
	 * <p>Sharing a key with a vanilla binding works here and would not have in older
	 * versions: 26.2's {@code KeyMapping.MAP} holds a <em>list</em> per key and
	 * {@code click}/{@code set} go through {@code forAllKeyMappings}, so both mappings
	 * receive the press. Vanilla's half is suppressed in two places rather than by
	 * unbinding it, which would rewrite the player's options file: {@link #register()}
	 * drains its clicks in the world, and {@code ContainerScreenInventoryKeyMixin}
	 * ignores it inside a screen. Both are needed — they are different code paths, and
	 * a key that still closes screens breaks the wheel's own inventory button.
	 */
	public static final KeyMapping RADIAL_MENU = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.grandcraft.radial_menu",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_E,
			CATEGORY));

	/**
	 * The four ability keys, {@code 1} through {@code 4}. Slot 3 — key {@code 4} — is
	 * the ultimate.
	 *
	 * <p><strong>The number keys were already free.</strong> {@link #register} has
	 * drained {@code options.keyHotbarSlots} every tick since the radial wheel replaced
	 * the hotbar, because there was no longer anything for them to select. So these need
	 * no new suppression: 26.2 holds a <em>list</em> of mappings per key and delivers the
	 * press to all of them, and vanilla's half is already being thrown away.
	 *
	 * <p>An array rather than four fields because every use of them is a loop — register
	 * them, drain them, send the slot. The index <em>is</em> the slot, which is what
	 * keeps that true.
	 */
	public static final KeyMapping[] ABILITY_SLOTS = new KeyMapping[SkillLoadout.SLOTS];

	static {
		int[] keys = { GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4 };

		for (int slot = 0; slot < ABILITY_SLOTS.length; slot++) {
			ABILITY_SLOTS[slot] = KeyMappingHelper.registerKeyMapping(new KeyMapping(
					"key.grandcraft.ability_" + (slot + 1),
					InputConstants.Type.KEYSYM,
					keys[slot],
					CATEGORY));
		}
	}

	private GrandCraftKeyMappings() {
	}

	/**
	 * Whether the wheel's key is physically down, asked of the window rather than of
	 * the mapping.
	 *
	 * <p>{@code KeyMapping.isDown} is not usable here: vanilla releases every mapping
	 * when a screen opens, so a key held across the inventory reads as released and
	 * then pressed again. {@link RadialMenu#tick} explains what that costs. This is the
	 * same question vanilla's own {@code KeyMapping.setAll} asks when the window
	 * regains focus, and the same guard it puts on it — a mapping bound to a mouse
	 * button or to nothing has no keyboard state to read, and falls back to the
	 * mapping, which is correct in game and merely stale inside a screen.
	 */
	private static boolean radialKeyHeld(Minecraft client) {
		InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(RADIAL_MENU);

		if (bound.getType() != InputConstants.Type.KEYSYM
				|| bound.getValue() == InputConstants.UNKNOWN.getValue()) {
			return RADIAL_MENU.isDown();
		}

		return InputConstants.isKeyDown(client.getWindow(), bound.getValue());
	}

	/**
	 * Turns presses of the four ability keys into one request each.
	 *
	 * <p>Drained the same way the dodge key is, and for the same reason: a press
	 * buffered through a lag spike must not fire the ability twice the moment the game
	 * catches up. One send per tick per slot at most, however many clicks were queued.
	 *
	 * <p>The client decides nothing. It does not check what is equipped, whether it is
	 * unlocked, or whether the press should do anything — it reports the key, and the
	 * server answers from its own record. That is the same no-prediction contract every
	 * other verb in this mod keeps.
	 */
	private static void sendAbilityPresses(Minecraft client) {
		if (client.player == null) {
			return;
		}

		for (int slot = 0; slot < ABILITY_SLOTS.length; slot++) {
			boolean pressed = false;

			while (ABILITY_SLOTS[slot].consumeClick()) {
				pressed = true;
			}

			if (pressed) {
				ClientPlayNetworking.send(new UseSkillPayload(slot));
			}
		}
	}

	public static void register() {
		// Runs at the head of Minecraft.tick, which is before handleKeybinds reads
		// these in the same tick — so draining them here is what removes vanilla's
		// action without a mixin. Click counts are per mapping, so emptying vanilla's
		// leaves GrandCraft's own bindings on the same keys untouched.
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			// The inventory is reached through the wheel's bottom wedge now. This half
			// covers being in the world; ContainerScreenInventoryKeyMixin covers being
			// inside a screen, which reads the mapping a different way.
			while (client.options.keyInventory.consumeClick()) {
				// drained
			}

			// The hotbar is gone, so the number keys have nothing to select.
			for (KeyMapping slot : client.options.keyHotbarSlots) {
				while (slot.consumeClick()) {
					// drained
				}
			}

			// Here rather than in END_CLIENT_TICK, and it is not a preference: by the tail
			// of the tick the player has already left the ground, so a ground jump would
			// read as a mid-air one. See ClientAcrobat.
			ClientAcrobat.tick(client);

			// Vanilla reads jump through isDown, so nothing consumes these and the queue
			// would grow for as long as the game runs. Drained rather than read, because
			// the dash is driven by an edge — a held key manufactures clicks here.
			while (client.options.keyJump.consumeClick()) {
				// drained
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			RadialMenu.tick(client, radialKeyHeld(client));

			// Nothing reads these — the wheel is driven by the key's physical state —
			// but an unread click queue grows for as long as the game runs.
			while (RADIAL_MENU.consumeClick()) {
				// drained
			}

			while (OPEN_MENU.consumeClick()) {
				client.setScreenAndShow(new GrandCraftScreen());
			}

			// Drained rather than acted on once per press, so a press buffered during a
			// lag spike does not queue up a second dodge the moment the first ends.
			boolean dodged = false;

			while (DODGE.consumeClick()) {
				if (!dodged) {
					dodged = ClientDodge.request(client);
				}
			}

			sendAbilityPresses(client);
		});
	}
}
