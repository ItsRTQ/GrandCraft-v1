package com.hrtq.grandcraft.client;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.client.gui.GrandCraftScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
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

	private GrandCraftKeyMappings() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
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
		});
	}
}
