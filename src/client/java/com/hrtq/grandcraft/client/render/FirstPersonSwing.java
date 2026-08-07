package com.hrtq.grandcraft.client.render;

import com.hrtq.grandcraft.client.ClientCombatPhases;
import com.hrtq.grandcraft.client.ClientGameSettings;
import com.hrtq.grandcraft.combat.CombatState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;

/**
 * Drives the first-person hand through the attack, on the combat phases rather than on
 * vanilla's swing timer.
 *
 * <p><strong>This is the file to edit when the first-person swing is mistimed, too big or
 * too small.</strong> Sibling of {@link AttackAnimation}, which does the same job for the
 * third-person model — but where that one draws an authored clip, this one re-times
 * vanilla's own hand swing. Why, and why not the clip, is the whole design note below.
 *
 * <h2>Why there was nothing to see</h2>
 *
 * <p>First person got its swing for free until 2026-08-07: a click called
 * {@code LocalPlayer.swing}, that set {@code attackAnim}, and
 * {@code ItemInHandRenderer.submitHandsWithItems} reads {@code getAttackAnim} as the first
 * thing it does. {@code MinecraftAttackSwingMixin} stopped the click swinging anything —
 * necessary, because an optimistic swing the server then refused was a phantom attack —
 * and that left {@code attackAnim} pinned at zero and the hand perfectly still.
 *
 * <p>So the value is supplied here instead, from the phase the server confirmed.
 * <strong>The rendering path is vanilla's, untouched.</strong>
 *
 * <h2>Re-timing vanilla's arc, not retargeting the clip</h2>
 *
 * <p>A first attempt transformed the {@code PoseStack} from the authored clip's arm
 * direction. It showed nothing, and reading the method's bytecode says why twice over: the
 * transform landed <em>before</em> vanilla's own {@code mulPose(viewXRot)} /
 * {@code mulPose(viewYRot)}, so it was working in a frame that is not the hand's; and the
 * motion it added was a fraction of the one the hand had just lost. Chasing that would
 * mean rebuilding vanilla's swing arc in the right space to make it look like an arm two
 * metres away — for a request that was explicitly <em>"doesn't have to be to the dot but
 * slightly"</em> (user, 2026-08-07).
 *
 * <p>Vanilla's arc already is a swing, already lives in the right space, and is already
 * tuned for a hand held against the camera. What it lacked was <em>this mod's timing</em>.
 * Giving it that is what makes the two views resemble each other: the hand moves when the
 * third-person arm moves, for as long as the wind-up lasts, and lands with the blow. That
 * is the resemblance a player can actually perceive — the shape of a hand's arc at arm's
 * length is not.
 *
 * <p>It also follows the timing for free. The wind-up is a global on
 * {@code /grandcraft config combat}, so a longer one stretches this with no number here to
 * change, and a weapon that modifies it later will stretch it too.
 */
public final class FirstPersonSwing {
	/**
	 * How much of vanilla's swing arc the wind-up spends.
	 *
	 * <p>Vanilla's arc runs 0 to 1 and takes the hand out and back. Spending only the
	 * front of it on the wind-up leaves the bulk — the fast part, and the part that reads
	 * as the blow — for the active window, which is when the damage lands and when the
	 * third-person clip plays its own strike. So the hand drifts while the swing is being
	 * charged and travels when it is thrown.
	 *
	 * <p>Raise it for a first person that leads more with the wind-up; lower it toward 0
	 * for a hand that stays still until the strike.
	 */
	private static final float WIND_UP_SHARE = 0.25F;

	/** No override: the caller should use whatever vanilla was going to. */
	public static final float NOT_ATTACKING = -1.0F;

	private FirstPersonSwing() {
	}

	/**
	 * Where through vanilla's swing arc the hand should be this frame, or
	 * {@link #NOT_ATTACKING}.
	 *
	 * <p>Reads the phase against the wall clock like everything else that animates, so it
	 * is smooth between ticks rather than stepping five times across a short wind-up.
	 *
	 * <p>The recovery holds the arc at its end, which is where vanilla leaves the hand at
	 * rest — the endlag is a lockout, not a motion, and the third-person clip spends it
	 * fading back too.
	 */
	public static float progress(LocalPlayer player) {
		if (!ClientGameSettings.current().combatAnimations()) {
			return NOT_ATTACKING;
		}

		long now = Util.getMillis();
		CombatState phase = ClientCombatPhases.stateOf(player.getId(), now);

		if (!phase.isAttack()) {
			return NOT_ATTACKING;
		}

		float t = Math.clamp(ClientCombatPhases.progressOf(player.getId(), now), 0.0F, 1.0F);

		return switch (phase) {
			case ATTACK_STARTUP -> t * WIND_UP_SHARE;
			case ATTACK_ACTIVE -> WIND_UP_SHARE + t * (1.0F - WIND_UP_SHARE);
			default -> 1.0F;
		};
	}
}
