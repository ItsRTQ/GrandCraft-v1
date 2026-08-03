package com.hrtq.grandcraft.client;

import com.hrtq.grandcraft.client.animation.GrandCraftAnimations;
import com.hrtq.grandcraft.client.hud.ManaBarElement;
import com.hrtq.grandcraft.client.hud.StaminaBarElement;
import com.hrtq.grandcraft.client.render.LifeEssenceOrbRenderer;
import com.hrtq.grandcraft.client.render.ZombieHumanRenderer;
import com.hrtq.grandcraft.client.tooltip.WeaponTooltip;
import com.hrtq.grandcraft.combat.CombatPhaseView;
import com.hrtq.grandcraft.combat.WeaponSettings;
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
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.util.Util;

public class GrandCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		GrandCraftKeyMappings.register();
		GrandCraftClientNetworking.register();

		// The animator's clips for actors on the vanilla rig. Safe this early: the
		// reload-listener registry is not resolved until CLIENT_STARTED.
		GrandCraftAnimations.register();

		// Lets GeckoLib animation controllers, which are registered on entities in the
		// common source set, read the phase data that only exists on the client.
		CombatPhaseView.set(new ClientCombatPhaseView());

		// Weapon damage is now a fact about its holder, so the item tooltip has to say
		// so. Paired with ItemStackAttributeTooltipMixin, which takes vanilla's now-false
		// damage line back out.
		WeaponTooltip.register();

		// EntityRendererRegistry is marked deprecated as a class, but there is no
		// replacement in this Fabric version: vanilla's EntityRenderers.register is
		// private and Fabric reaches it by mixin. The warning is expected; do not go
		// looking for a newer API.
		EntityRendererRegistry.register(GrandCraftEntities.ZOMBIE_HUMAN, ZombieHumanRenderer::new);
		EntityRendererRegistry.register(GrandCraftEntities.LIFE_ESSENCE_ORB, LifeEssenceOrbRenderer::new);

		// Vanilla's own item-sprite projectile renderer, which is the whole of "use the
		// wind charge sprite": the entity reports that item and this draws it.
		EntityRendererRegistry.register(GrandCraftEntities.GUST, ThrownItemRenderer::new);

		// Attached to the food bar so the stamina bar inherits vanilla's own rule for
		// when status bars are shown, and declared as a right-hand row so the air bar
		// moves up rather than drawing over it. Both registries freeze once the client
		// has started, which is well after this entrypoint runs.
		HudElementRegistry.attachElementAfter(
				VanillaHudElements.FOOD_BAR, StaminaBarElement.ID, new StaminaBarElement());
		HudStatusBarHeightRegistry.addRight(
				StaminaBarElement.ID, player -> StaminaBarElement.reservedHeight());

		// Mana rides the stamina bar's layer for the same reason stamina rides the
		// food bar's. The right-hand column stacks upward from the food bar, so
		// attaching after stamina puts mana one row *above* it — there is no room
		// below, and moving the confirmed-working stamina element to swap them would
		// be a regression risk for no mechanical gain.
		HudElementRegistry.attachElementAfter(
				StaminaBarElement.ID, ManaBarElement.ID, new ManaBarElement());
		HudStatusBarHeightRegistry.addRight(
				ManaBarElement.ID, player -> ManaBarElement.reservedHeight());

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
				ClientWeaponSettings.set(WeaponSettings.DEFAULT);
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
