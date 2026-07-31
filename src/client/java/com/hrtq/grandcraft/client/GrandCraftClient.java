package com.hrtq.grandcraft.client;

import com.hrtq.grandcraft.client.hud.StaminaBarElement;
import com.hrtq.grandcraft.config.GameSettings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.util.Util;

public class GrandCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		GrandCraftKeyMappings.register();
		GrandCraftClientNetworking.register();

		// Attached to the food bar so the stamina bar inherits vanilla's own rule for
		// when status bars are shown, and declared as a right-hand row so the air bar
		// moves up rather than drawing over it. Both registries freeze once the client
		// has started, which is well after this entrypoint runs.
		HudElementRegistry.attachElementAfter(
				VanillaHudElements.FOOD_BAR, StaminaBarElement.ID, new StaminaBarElement());
		HudStatusBarHeightRegistry.addRight(
				StaminaBarElement.ID, player -> StaminaBarElement.reservedHeight());

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null) {
				// Between worlds: drop the tracking so entity ids from the old one
				// cannot be mistaken for entities in the next, and reset to defaults
				// so a single player world does not inherit a server's settings.
				HealthBarTracker.clear();
				ClientAttackLockout.clear();
				ClientStamina.clear();
				ClientCombatPhases.clear();
				ClientGameSettings.set(GameSettings.DEFAULT);
				return;
			}

			if (ClientGameSettings.current().healthBars()) {
				HealthBarTracker.tick(client.level, Util.getMillis());
			}

			// Runs regardless of the animation setting: the sweep is what stops a
			// dropped packet leaving an actor posed forever, and turning animations
			// off mid-swing must not be the thing that strands one.
			ClientCombatPhases.tick(client.level, Util.getMillis());
		});
	}
}
