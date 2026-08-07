package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.render.FirstPersonSwing;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Gives the first-person hand this mod's swing timing.
 *
 * <p>Deliberately thin: <em>when</em> the hand is through its swing is
 * {@link FirstPersonSwing}'s, and this only says where that answer is injected. Sibling of
 * {@code PlayerModelMixin}, which does the same job for the third-person model.
 *
 * <h2>One value, at the top of the method</h2>
 *
 * <p>{@code submitHandsWithItems} reads {@code getAttackAnim} as its first act and lerps
 * the hand's whole position from it — verified against the 26.2 jar, where it is the call
 * at offset 3 and feeds both {@code submitArmWithItem} calls. Replacing that one number
 * therefore re-times vanilla's entire first-person swing while leaving every pixel of the
 * rendering to vanilla.
 *
 * <p><strong>Why not transform the {@code PoseStack}.</strong> That was tried first and
 * showed nothing. The bytecode says why: the method applies its own
 * {@code mulPose(viewXRot)} and {@code mulPose(viewYRot)} at offsets 92 and 116, so a
 * transform injected at HEAD is composed in a frame that is not the hand's — and it was
 * competing with the swing that had just been taken away rather than replacing it.
 *
 * <p>Scoped to this method only. {@code getAttackAnim} drives third-person rendering and
 * vanilla damage scaling elsewhere, and neither should hear about this.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Redirect(
			method = "submitHandsWithItems",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/player/LocalPlayer;getAttackAnim(F)F"))
	private float grandcraft$phaseSwing(LocalPlayer player, float partialTick) {
		float progress = FirstPersonSwing.progress(player);

		// Vanilla's own value when this mod has nothing to say — mining still swings the
		// hand, and so does anything else that calls LocalPlayer.swing.
		return progress == FirstPersonSwing.NOT_ATTACKING
				? player.getAttackAnim(partialTick)
				: progress;
	}
}
