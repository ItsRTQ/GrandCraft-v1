package com.hrtq.grandcraft.client.render;

import com.geckolib.renderer.GeoEntityRenderer;
import com.hrtq.grandcraft.entity.DemonSkeletonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Draws the demonic skeleton.
 *
 * <p>{@link GeoEntityRenderer} supplies the whole 26.2 render-state chain, so there is
 * nothing to override — see {@link ZombieHumanRenderer}.
 */
public class DemonSkeletonRenderer extends GeoEntityRenderer<DemonSkeletonEntity, LivingEntityRenderState> {
	public DemonSkeletonRenderer(EntityRendererProvider.Context context) {
		super(context, new DemonSkeletonModel());
	}
}
