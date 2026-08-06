package com.hrtq.grandcraft.client.render;

import com.hrtq.grandcraft.combat.CombatState;
import com.hrtq.grandcraft.combat.WeaponCategory;

/**
 * The combat phase an entity was in when its render state was extracted.
 *
 * <p>26.2 draws entities from an extracted state object rather than from the
 * entity, and no vanilla render state carries anything about combat. Mixed into
 * {@code LivingEntityRenderState} so the value can be read back in {@code setupAnim}
 * and {@code setupRotations}, both of which are handed the state and nothing else —
 * in particular, neither can reach the entity or its id.
 *
 * <p>Deliberately mixed in at the living-entity level rather than onto
 * {@code ZombieRenderState}: the phases are the same for every actor, so every
 * humanoid already carries the field by the time a second one is posed.
 */
public interface CombatPoseState {
	CombatState grandcraft$phase();

	/** How far through that phase, from 0 to 1, interpolated between ticks. */
	float grandcraft$phaseProgress();

	/**
	 * The heading of an in-flight dodge in world degrees, or null when none has been
	 * sampled — in which case the step leans straight forward.
	 */
	Float grandcraft$travelYaw();

	/**
	 * What the actor was holding, which is what decides <em>which</em> attack clip plays.
	 *
	 * <p>Carried on the state rather than read at pose time for the reason every other
	 * field here is: {@code setupAnim} is handed the state and cannot reach the entity.
	 * Null for anything that is not a combatant, which reads as "no authored swing" and
	 * falls back to the procedural posture.
	 */
	WeaponCategory grandcraft$weapon();

	void grandcraft$setPhase(CombatState phase, float progress, Float travelYaw,
			WeaponCategory weapon);
}
