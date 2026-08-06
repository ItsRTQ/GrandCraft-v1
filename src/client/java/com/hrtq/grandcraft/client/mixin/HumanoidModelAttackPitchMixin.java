package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.render.AttackAnimation;
import com.hrtq.grandcraft.client.render.CombatPoseState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Turns vanilla's look-pitch drag on the attacking arm upside down.
 *
 * <p><strong>This is an experiment, and it is one constant to switch off</strong> — see
 * {@link #FLIP}. It exists because the player's swing clips through the body and gets
 * worse the further down the player looks, and the arithmetic underneath that is known.
 *
 * <h2>What vanilla does</h2>
 *
 * {@code HumanoidModel.setupAttackAnimation} folds the head's pitch into the arm it is
 * swinging:
 *
 * <pre>
 * f = sin(attackTime * PI) * -(head.xRot - 0.7) * 0.75
 * arm.xRot -= sin(outQuart(attackTime) * PI) * 1.2 + f
 * </pre>
 *
 * <p>{@code head.xRot} is the look pitch, growing positive as the player looks down. So
 * the term pulls the arm one way at level and the other way past 0.7 radians (40°), and
 * {@code HumanoidClipPose} composes the authored clip on top of whatever that left.
 *
 * <p>Redirecting the read to {@code 1.4 - head.xRot} mirrors it about {@code 0.7}, which
 * negates the whole term exactly while leaving its magnitude and its neutral point alone.
 * Vanilla's swing still runs — only the direction the pitch drags it in changes. That is
 * the difference between this and the attempt before it, which removed vanilla's swing
 * altogether and looked markedly worse.
 *
 * <h2>Why the opcode is spelled out</h2>
 *
 * There are five {@code ModelPart.xRot} accesses in the method and only three are reads.
 * {@code head.xRot} is <strong>ordinal 1 of the reads</strong> but index 2 of all five, so
 * without {@code opcode = GETFIELD} the ordinal would land on the wrong instruction — a
 * {@code putfield} on {@code leftArm}. Both numbers were counted off the disassembly, and
 * they are what to re-check after a Minecraft update.
 *
 * <p>Scoped to the player mid-swing with an authored clip: a mob's {@code attackTime} is
 * already zeroed by {@code AbstractZombieModelMixin}, and a bow or an empty hand has no
 * clip to conflict with, so both keep vanilla's pitch exactly as it is.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelAttackPitchMixin {
	/** Set false to hand the pitch drag back to vanilla, unchanged. */
	private static final boolean FLIP = true;

	/**
	 * The axis the original term is mirrored about — vanilla's own {@code 0.7}, doubled.
	 * At exactly 0.7 radians of pitch the flip is a no-op, which is what makes it a
	 * reversal rather than an offset.
	 */
	private static final float MIRROR = 1.4F;

	@Redirect(
			method = "setupAttackAnimation",
			at = @At(
					value = "FIELD",
					target = "Lnet/minecraft/client/model/geom/ModelPart;xRot:F",
					opcode = Opcodes.GETFIELD,
					ordinal = 1))
	private float grandcraft$flipAttackPitch(ModelPart head, HumanoidRenderState state) {
		if (!FLIP
				|| !((Object) this instanceof PlayerModel)
				|| !(state instanceof CombatPoseState pose)
				|| !pose.grandcraft$phase().isAttack()
				|| !AttackAnimation.hasClip(pose.grandcraft$weapon())) {
			return head.xRot;
		}

		return MIRROR - head.xRot;
	}
}
