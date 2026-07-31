package com.hrtq.grandcraft.client;

import com.hrtq.grandcraft.combat.CombatState;
import com.hrtq.grandcraft.network.CombatPhasePayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Which combat phase each visible actor is in, as told by the server.
 *
 * <p>Follows {@link ClientAttackLockout}: one packet per transition, counted out
 * locally against the wall clock. Reading against millis rather than ticks is what
 * makes the animation smooth — a pose driven by whole ticks would step five times
 * across a five tick wind-up, which at 250ms reads as a stutter rather than a
 * movement.
 *
 * <p>Unlike {@link HealthBarTracker} this cannot be rebuilt from the entity list
 * each tick, because nothing about a combat phase is synced by vanilla — the map
 * is fed by packets and therefore has to be pruned. Entries are removed as soon as
 * an actor returns to neutral, so in practice it holds only the handful of actors
 * mid-swing.
 *
 * <p>Keyed on network entity id, which is only meaningful within one connection —
 * hence {@link #clear()} on disconnect.
 */
public final class ClientCombatPhases {
	/**
	 * How long past its end an entry is kept before being swept.
	 *
	 * <p>Only a backstop. The server sends a neutral packet at the end of every
	 * phase; this covers the case where that packet never arrives, so a zombie
	 * cannot be left frozen mid-wind-up forever.
	 */
	private static final long STALE_GRACE_MILLIS = 1000L;

	/** Horizontal speed, squared, above which a dodge's direction is worth trusting. */
	private static final double STEP_SPEED_SQR = 0.04;

	private static Map<Integer, Phase> phases = new HashMap<>();

	/**
	 * The world-space direction each in-flight dodge is travelling, in degrees.
	 *
	 * <p>Latched rather than read live. The phase packet is sent the moment the dodge
	 * begins, but the velocity that dodge produces only reaches the client with the
	 * entity's next movement update — so the first frame or two would sample a stale
	 * direction, and by the end of the dodge friction has decayed it into noise. The
	 * first sample fast enough to mean anything is kept for the whole dodge.
	 */
	private static Map<Integer, Float> travelYaws = new HashMap<>();

	private ClientCombatPhases() {
	}

	private record Phase(CombatState state, long startMillis, long endMillis) {
		float progress(long nowMillis) {
			long span = this.endMillis - this.startMillis;

			if (span <= 0L) {
				return 1.0F;
			}

			double raw = (nowMillis - this.startMillis) / (double) span;

			return (float) Math.clamp(raw, 0.0, 1.0);
		}
	}

	public static void accept(CombatPhasePayload payload, long nowMillis) {
		CombatState state = CombatState.byId(payload.phase());

		// Neutral is the absence of a pose, so it is stored as an absence. That keeps
		// the map to actors actually mid-swing rather than to everything ever seen.
		if (state == CombatState.NEUTRAL || payload.totalTicks() <= 0) {
			phases.remove(payload.entityId());
			travelYaws.remove(payload.entityId());
			return;
		}

		// A new dodge starts a new direction; its recovery half keeps the one the
		// protected half latched, so the step does not change heading halfway through.
		if (state == CombatState.DODGE_ACTIVE) {
			travelYaws.remove(payload.entityId());
		}

		// Remaining and total differ only when catching up on a phase that began
		// before this client could see the actor. Backdating the start is what lets
		// that actor drop straight into the middle of its wind-up instead of
		// restarting it and lying about when the hit lands.
		long elapsed = (payload.totalTicks() - payload.remainingTicks()) * 50L;

		phases.put(payload.entityId(), new Phase(
				state,
				nowMillis - elapsed,
				nowMillis + payload.remainingTicks() * 50L));
	}

	/** The phase this entity is in, or NEUTRAL if it is not mid-swing. */
	public static CombatState stateOf(int entityId, long nowMillis) {
		Phase phase = phases.get(entityId);

		if (phase == null || nowMillis >= phase.endMillis()) {
			return CombatState.NEUTRAL;
		}

		return phase.state();
	}

	/** How far through its current phase this entity is, from 0 to 1. */
	public static float progressOf(int entityId, long nowMillis) {
		Phase phase = phases.get(entityId);

		return phase == null ? 0.0F : phase.progress(nowMillis);
	}

	/** The latched dodge heading in degrees, or null if none has been sampled yet. */
	public static Float travelYaw(int entityId) {
		return travelYaws.get(entityId);
	}

	/**
	 * Sweeps entries the server stopped talking about, and samples the heading of any
	 * dodge in flight.
	 *
	 * <p>Cheap enough to run every tick because the map only ever holds actors that
	 * are mid-action right now.
	 *
	 * <p>Sampling belongs here rather than in the renderer, which was where it first
	 * lived: an entity is only extracted for rendering if it is actually drawn, and
	 * your own player is not drawn in first person. That left the local player's
	 * heading permanently unknown — so every dodge fell back to the straight-ahead
	 * default and the camera never tilted for a sideways one, which is precisely the
	 * case the split exists to show.
	 */
	public static void tick(ClientLevel level, long nowMillis) {
		Iterator<Map.Entry<Integer, Phase>> entries = phases.entrySet().iterator();

		while (entries.hasNext()) {
			Map.Entry<Integer, Phase> entry = entries.next();
			int entityId = entry.getKey();
			Entity entity = level.getEntity(entityId);

			if (nowMillis > entry.getValue().endMillis() + STALE_GRACE_MILLIS || entity == null) {
				travelYaws.remove(entityId);
				entries.remove();
				continue;
			}

			if (entry.getValue().state().isDodge()) {
				sampleTravelYaw(entityId, entity);
			}
		}
	}

	/**
	 * Records the direction a dodge is travelling, if one is not already known.
	 *
	 * <p>First usable sample wins and is then held for the rest of the dodge — see
	 * {@link #travelYaws} for why reading it live does not work.
	 */
	private static void sampleTravelYaw(int entityId, Entity entity) {
		if (travelYaws.containsKey(entityId)) {
			return;
		}

		Vec3 motion = entity.getKnownMovement();
		double speedSqr = motion.x * motion.x + motion.z * motion.z;

		// Ordinary walking is well under this, so it cannot be mistaken for a launch.
		if (speedSqr < STEP_SPEED_SQR) {
			return;
		}

		// Inverse of Minecraft's own forward vector, which for yaw t is (-sin t, cos t).
		travelYaws.put(entityId, (float) Math.toDegrees(Math.atan2(-motion.x, motion.z)));
	}

	/** Dropped on disconnect: entity ids are only meaningful within one connection. */
	public static void clear() {
		phases = new HashMap<>();
		travelYaws = new HashMap<>();
	}
}
