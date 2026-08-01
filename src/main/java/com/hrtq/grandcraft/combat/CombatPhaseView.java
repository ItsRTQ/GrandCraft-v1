package com.hrtq.grandcraft.combat;

/**
 * A read-only view of what combat phase an entity is in, as the local client
 * understands it.
 *
 * <h2>Why this indirection exists</h2>
 * GeckoLib animation controllers are registered on the entity, which lives in the
 * common source set, but the phase data they need lives in {@code ClientCombatPhases}
 * in the client source set. A common class cannot reference it. Rather than have
 * every animated mob solve that individually — or reach across with an environment
 * check that would still class-load the wrong thing on a dedicated server — the
 * client installs its own implementation here at start-up and common code asks
 * through this interface.
 *
 * <p>Left at {@link #NEUTRAL_ONLY} on a server, where nothing animates and the honest
 * answer to every question is "not in a phase".
 */
public interface CombatPhaseView {
	/** The answer on the server, and before the client has installed its view. */
	CombatPhaseView NEUTRAL_ONLY = new CombatPhaseView() {
		@Override
		public CombatState stateOf(int entityId) {
			return CombatState.NEUTRAL;
		}

		@Override
		public float phaseTicksOf(int entityId) {
			return 0.0F;
		}
	};

	/**
	 * @param entityId the client-side entity id
	 * @return the phase that entity is in, never null; {@link CombatState#NEUTRAL}
	 *         when it is not in one or is unknown
	 */
	CombatState stateOf(int entityId);

	/**
	 * How long the entity's current phase lasts in total, in ticks.
	 *
	 * <p>The <em>whole</em> phase, not what is left of it, so it stays constant for
	 * the phase's duration and can be used as a denominator. That is what lets a clip
	 * be stretched to fit: a five tick wind-up and a fifteen tick one play the same
	 * animation at different speeds rather than the same speed for different lengths.
	 *
	 * @return 0 when the entity is in no phase, which callers must treat as "do not
	 *         scale" rather than dividing by it
	 */
	float phaseTicksOf(int entityId);

	static CombatPhaseView get() {
		return Holder.current;
	}

	static void set(CombatPhaseView view) {
		Holder.current = view;
	}

	/**
	 * Interfaces cannot hold mutable state, so the field lives on a nested class.
	 * {@link #get} and {@link #set} are the API.
	 */
	final class Holder {
		private static CombatPhaseView current = NEUTRAL_ONLY;

		private Holder() {
		}
	}
}
