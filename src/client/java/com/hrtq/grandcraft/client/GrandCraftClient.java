package com.hrtq.grandcraft.client;

import com.hrtq.grandcraft.client.hud.StaminaBarElement;
import com.hrtq.grandcraft.client.render.LifeEssenceOrbRenderer;
import com.hrtq.grandcraft.client.render.ZombieHumanRenderer;
import com.hrtq.grandcraft.combat.CombatPhaseView;
import com.hrtq.grandcraft.config.GameSettings;
import com.hrtq.grandcraft.entity.GrandCraftEntities;
import com.hrtq.grandcraft.progression.LevelSettings;
import com.hrtq.grandcraft.stats.StatSettings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
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

		// Lets GeckoLib animation controllers, which are registered on entities in the
		// common source set, read the phase data that only exists on the client.
		CombatPhaseView.set(new ClientCombatPhaseView());

		// EntityRendererRegistry is marked deprecated as a class, but there is no
		// replacement in this Fabric version: vanilla's EntityRenderers.register is
		// private and Fabric reaches it by mixin. The warning is expected; do not go
		// looking for a newer API.
		EntityRendererRegistry.register(GrandCraftEntities.ZOMBIE_HUMAN, ZombieHumanRenderer::new);
		EntityRendererRegistry.register(GrandCraftEntities.LIFE_ESSENCE_ORB, LifeEssenceOrbRenderer::new);

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
				ClientMana.clear();
				ClientCombatPhases.clear();
				ClientGuard.clear();
				ClientGameSettings.set(GameSettings.DEFAULT);
				ClientStatSettings.set(StatSettings.DEFAULT);
				ClientLevelSettings.set(LevelSettings.DEFAULT);
				return;
			}

			if (ClientGameSettings.current().healthBars()) {
				HealthBarTracker.tick(client.level, Util.getMillis());
			}

			// Runs regardless of the animation setting: the sweep is what stops a
			// dropped packet leaving an actor posed forever, and turning animations
			// off mid-swing must not be the thing that strands one.
			ClientCombatPhases.tick(client.level, Util.getMillis());

			// Deliberately last, and deliberately at the end of the tick: the hold is
			// not latched until handleKeybinds has already had this tick's press, which
			// is what leaves ordinary right-click interaction working. See ClientGuard.
			ClientGuard.tick(client);
		});
	}
}
