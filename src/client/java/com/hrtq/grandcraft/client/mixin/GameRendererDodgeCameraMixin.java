package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.ClientGameSettings;
import com.hrtq.grandcraft.client.render.DodgeCamera;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hangs the first-person dodge roll off vanilla's camera transform.
 *
 * <p>Targets {@code bobHurt} rather than {@code bobView} deliberately: vanilla only
 * calls {@code bobView} when the View Bobbing option is on, and a defensive verb's
 * feedback must not disappear because of a graphics setting. Both multiply into the
 * same PoseStack, which is then folded into the projection matrix, so the effect is
 * identical and only the reliability differs.
 *
 * <p>All the motion is in {@link DodgeCamera} — this only decides whether to ask.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererDodgeCameraMixin {
	@Inject(
			method = "bobHurt(Lnet/minecraft/client/renderer/state/level/CameraRenderState;"
					+ "Lcom/mojang/blaze3d/vertex/PoseStack;)V",
			at = @At("TAIL"))
	private void grandcraft$dodgeCamera(CameraRenderState state, PoseStack poseStack,
			CallbackInfo info) {
		if (!ClientGameSettings.current().combatAnimations()) {
			return;
		}

		LocalPlayer player = Minecraft.getInstance().player;

		if (player != null) {
			DodgeCamera.apply(poseStack, player.getId(), player.getYRot(), Util.getMillis());
		}
	}
}
