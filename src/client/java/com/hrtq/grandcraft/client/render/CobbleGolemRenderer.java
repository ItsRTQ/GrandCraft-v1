package com.hrtq.grandcraft.client.render;

import com.geckolib.renderer.GeoEntityRenderer;
import com.hrtq.grandcraft.entity.CobbleGolemEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Draws the cobble golem.
 *
 * <p>{@link GeoEntityRenderer} supplies the whole 26.2 render-state chain, so there is
 * nothing to override — see {@link ZombieHumanRenderer}.
 */
public class CobbleGolemRenderer extends GeoEntityRenderer<CobbleGolemEntity, LivingEntityRenderState> {
	public CobbleGolemRenderer(EntityRendererProvider.Context context) {
		super(context, new CobbleGolemModel());
	}
}
