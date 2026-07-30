package com.hrtq.grandcraft.client;

import com.hrtq.grandcraft.client.gui.CombatConfigScreen;
import com.hrtq.grandcraft.network.OpenCombatConfigPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class GrandCraftClientNetworking {
	private GrandCraftClientNetworking() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(OpenCombatConfigPayload.TYPE, (payload, context) ->
				// Handlers already run on the client thread, so the screen can be
				// opened directly.
				context.client().setScreenAndShow(new CombatConfigScreen(payload.settings())));
	}
}
