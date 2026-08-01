package com.hrtq.grandcraft.client;

import com.hrtq.grandcraft.combat.CombatPhaseView;
import com.hrtq.grandcraft.combat.CombatState;
import net.minecraft.util.Util;

/**
 * Lets common-side GeckoLib controllers read {@link ClientCombatPhases}.
 *
 * <p>Installed by {@code GrandCraftClient} at start-up. The server never has one and
 * keeps {@link CombatPhaseView#NEUTRAL_ONLY}, so nothing here is ever class-loaded
 * outside a client.
 *
 * <p>Reads the wall clock per call rather than taking a tick, matching how the rest
 * of the client store works — the phase is counted out against millis so that a five
 * tick wind-up animates smoothly instead of stepping five times.
 */
public final class ClientCombatPhaseView implements CombatPhaseView {
	@Override
	public CombatState stateOf(int entityId) {
		return ClientCombatPhases.stateOf(entityId, Util.getMillis());
	}

	@Override
	public float phaseTicksOf(int entityId) {
		return ClientCombatPhases.phaseTicks(entityId, Util.getMillis());
	}
}
