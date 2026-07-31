package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.ClientGameSettings;
import com.hrtq.grandcraft.client.render.DodgeCamera;
import com.hrtq.grandcraft.client.render.StaggerCamera;
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
 * Hangs first-person combat feedback off vanilla's camera transform.
 *
 * <p>Targets {@code bobHurt} rather than {@code bobView} deliberately: vanilla only
 * calls {@code bobView} when the View Bobbing option is on, and a defensive verb's
 * feedback must not disappear because of a graphics setting. Both multiply into the
 * same PoseStack, which is then folded into the projection matrix, so the effect is
 * identical and only the reliability differs.
 *
 * <p>This is where anything the player has to <em>feel</em> rather than see belongs,
 * because the one thing first person cannot show is the player's own body. All the
 * motion lives in {@link DodgeCamera} and {@link StaggerCamera} — this only decides
 * whether to ask, and the two compose safely because at most one can be running.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererCombatCameraMixin {
	@Inject(
			method = "bobHurt(Lnet/minecraft/client/renderer/state/level/CameraRenderState;"
					+ "Lcom/mojang/blaze3d/vertex/PoseStack;)V",
			at = @At("TAIL"))
	private void grandcraft$combatCamera(CameraRenderState state, PoseStack poseStack,
			CallbackInfo info) {
		if (!ClientGameSettings.current().combatAnimations()) {
			return;
		}

		LocalPlayer player = Minecraft.getInstance().player;

		if (player == null) {
			return;
		}

		long now = Util.getMillis();

		DodgeCamera.apply(poseStack, player.getId(), player.getYRot(), now);
		StaggerCamera.apply(poseStack, player.getId(), now);
	}
}
