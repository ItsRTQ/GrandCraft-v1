package com.hrtq.grandcraft.client;

import com.hrtq.grandcraft.client.gui.CombatConfigScreen;
import com.hrtq.grandcraft.client.gui.GameConfigScreen;
import com.hrtq.grandcraft.client.gui.LevelConfigScreen;
import com.hrtq.grandcraft.client.gui.StatConfigScreen;
import com.hrtq.grandcraft.network.AttackLockoutPayload;
import com.hrtq.grandcraft.network.CombatPhasePayload;
import com.hrtq.grandcraft.network.GameConfigPayload;
import com.hrtq.grandcraft.network.LevelConfigPayload;
import com.hrtq.grandcraft.network.ManaPayload;
import com.hrtq.grandcraft.network.OpenCombatConfigPayload;
import com.hrtq.grandcraft.network.StaminaPayload;
import com.hrtq.grandcraft.network.StatConfigPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Util;

public final class GrandCraftClientNetworking {
	private GrandCraftClientNetworking() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(OpenCombatConfigPayload.TYPE, (payload, context) ->
				// Handlers already run on the client thread, so the screen can be
				// opened directly.
				context.client().setScreenAndShow(new CombatConfigScreen(payload.settings())));

		ClientPlayNetworking.registerGlobalReceiver(AttackLockoutPayload.TYPE, (payload, context) ->
				ClientAttackLockout.begin(payload.ticks(), Util.getMillis()));

		ClientPlayNetworking.registerGlobalReceiver(StaminaPayload.TYPE, (payload, context) ->
				ClientStamina.accept(payload, Util.getMillis()));

		ClientPlayNetworking.registerGlobalReceiver(CombatPhasePayload.TYPE, (payload, context) ->
				ClientCombatPhases.accept(payload, Util.getMillis()));

		ClientPlayNetworking.registerGlobalReceiver(ManaPayload.TYPE, (payload, context) ->
				ClientMana.accept(payload, Util.getMillis()));

		ClientPlayNetworking.registerGlobalReceiver(GameConfigPayload.TYPE, (payload, context) -> {
			// One packet serves both jobs: every client is told the new values so it
			// can render from them, and only the admin who asked gets the screen.
			ClientGameSettings.set(payload.settings());

			if (payload.openScreen()) {
				context.client().setScreenAndShow(new GameConfigScreen(payload.settings()));
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(StatConfigPayload.TYPE, (payload, context) -> {
			// Same double duty as the game settings above: everyone is told, only the
			// admin who asked gets the screen.
			ClientStatSettings.set(payload.settings());

			if (payload.openScreen()) {
				context.client().setScreenAndShow(new StatConfigScreen(payload.settings()));
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(LevelConfigPayload.TYPE, (payload, context) -> {
			// Same double duty again: everyone is told, only the admin who asked gets
			// the screen.
			ClientLevelSettings.set(payload.settings());

			if (payload.openScreen()) {
				context.client().setScreenAndShow(new LevelConfigScreen(payload.settings()));
			}
		});
	}
}
