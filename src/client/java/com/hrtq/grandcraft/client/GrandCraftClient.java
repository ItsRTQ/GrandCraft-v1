package com.hrtq.grandcraft.client;

import com.hrtq.grandcraft.client.animation.GrandCraftAnimations;
import com.hrtq.grandcraft.client.hud.AbilityBarElement;
import com.hrtq.grandcraft.client.hud.HealthBarElement;
import com.hrtq.grandcraft.client.hud.HudBars;
import com.hrtq.grandcraft.client.hud.ManaBarElement;
import com.hrtq.grandcraft.client.hud.RadialMenu;
import com.hrtq.grandcraft.client.hud.RadialMenuElement;
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
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.AttackIndicatorStatus;
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

		// Vanilla's hearts are removed outright rather than drawn over: the authored
		// health bar says the same thing, and two visual languages for one number is
		// worse than either. This also takes the absorption hearts with it — see
		// HealthBarElement, which does not show absorption.
		HudElementRegistry.removeElement(VanillaHudElements.HEALTH_BAR);

		// The hotbar goes the same way, and for the same reason: the radial wheel is
		// the mod's answer to "what am I holding, and what can I switch to", and a row
		// of nine boxes along the bottom of the screen is the second answer.
		//
		// It takes two other things with it, both deliberate. The offhand slot is no
		// longer displayed anywhere — the swap-hands key and the inventory screen are
		// how the offhand is used now. And the attack indicator drew inside this
		// element, which is what the next block is about.
		HudElementRegistry.removeElement(VanillaHudElements.HOTBAR);

		// The indicator has a second home vanilla already draws: under the crosshair,
		// from the separate crosshair element, reading the same
		// LocalPlayer.getAttackStrengthScale that PlayerAttackStrengthMixin overrides
		// with GrandCraft's lockout. So moving the player's setting across is the whole
		// job — the mixin needs no change and the feedback survives losing the hotbar.
		//
		// OFF is left alone. That is a player who asked for no indicator, and this is
		// not the code to argue with them.
		//
		// On CLIENT_STARTED rather than here: this entrypoint runs from inside
		// Minecraft's constructor, and reading options from there is a bet on how far
		// along it is.
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			if (client.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
				client.options.attackIndicator().set(AttackIndicatorStatus.CROSSHAIR);
				client.options.save();
			}
		});

		// All three bars are attached to the food bar's layer, in a chain, so they
		// inherit vanilla's own rule for when status bars are shown — they disappear in
		// creative and spectator with no gamemode check anywhere. The chain also fixes
		// their draw order, which is the order HudBars stacks them in.
		//
		// Where they draw is entirely HudBars' business now. Nothing declares a
		// right-hand status row any more: the bars moved to the top-left corner, so
		// reserving a row there would push the air bar up to clear a gap nothing fills.
		// HudElementRegistry freezes once the client has started, well after this runs.
		// Vanilla's hunger bar joins the column rather than staying in its own corner, so
		// everything the player reads about themselves is in one place. Wrapped, not
		// reimplemented — see HudBars.intoColumn(). It stays on its own layer, so it
		// keeps obeying the same "show status bars?" rule as the three below it.
		HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, HudBars.intoColumn());

		HudElementRegistry.attachElementAfter(
				VanillaHudElements.FOOD_BAR, HealthBarElement.ID, new HealthBarElement());
		HudElementRegistry.attachElementAfter(
				HealthBarElement.ID, StaminaBarElement.ID, new StaminaBarElement());
		HudElementRegistry.attachElementAfter(
				StaminaBarElement.ID, ManaBarElement.ID, new ManaBarElement());

		// Removing the hotbar left a strip of nothing along the bottom with the
		// experience bar floating above it. Push the experience bar and its level number
		// down into that strip, which frees the row above for the ability bar — so the
		// bottom of the screen reads ability bar, experience, edge.
		//
		// Wrapped rather than removed and reimplemented: the info bar has three modes in
		// 26.2 (experience, jump meter, locator) and taking it over would mean owning all
		// three forever. This is vanilla's own drawing, lower down.
		HudElementRegistry.replaceElement(VanillaHudElements.INFO_BAR,
				AbilityBarElement.shiftedBy(AbilityBarElement.XP_BAR_DROP));
		HudElementRegistry.replaceElement(VanillaHudElements.EXPERIENCE_LEVEL,
				AbilityBarElement.shiftedBy(AbilityBarElement.XP_BAR_DROP));

		// And the held-item name goes up, out of the way. Vanilla draws it at
		// guiHeight - 59, which is inside the ability bar — without this it would flash
		// across the artwork every time the wheel changes the held item.
		HudElementRegistry.replaceElement(VanillaHudElements.HELD_ITEM_TOOLTIP,
				AbilityBarElement.shiftedBy(AbilityBarElement.HELD_ITEM_LIFT));

		// The ability bar and the wheel are deliberately NOT on the status-bar column.
		// The three resource bars hang off the food bar so they inherit vanilla's "show
		// status bars?" rule and vanish in creative and spectator. That is right for a
		// resource and wrong for these two: the wheel is a menu the player opened, and
		// the ability bar says what four keys do — both are just as true in creative.
		//
		// Chaining the bar onto that column instead cost a debugging round: every
		// element on it disappeared in a creative test world and read as a broken HUD.
		//
		// Chained explicitly rather than both hanging off the held-item name, so the
		// order is stated rather than inherited from registration order — the wheel is
		// drawn last and therefore over the bar, which is the way round it has to be.
		HudElementRegistry.attachElementAfter(
				VanillaHudElements.HELD_ITEM_TOOLTIP, AbilityBarElement.ID, new AbilityBarElement());
		HudElementRegistry.attachElementAfter(
				AbilityBarElement.ID, RadialMenuElement.ID, new RadialMenuElement());

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
				ClientCombatMaster.clear();
				ClientGuard.clear();
				RadialMenu.clear();
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
