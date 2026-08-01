package com.hrtq.grandcraft.client.render;

import com.geckolib.renderer.GeoEntityRenderer;
import com.hrtq.grandcraft.entity.ZombieHumanEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Draws the zombie-human.
 *
 * <p>{@link GeoEntityRenderer} already supplies the whole 26.2 render-state chain —
 * {@code createRenderState}, {@code extractRenderState} and {@code submit} — so
 * there is nothing to override. The state type is
 * {@link LivingEntityRenderState} because that is what GeckoLib's own
 * {@code createRenderState} builds for anything that is a {@code LivingEntity}.
 */
public class ZombieHumanRenderer extends GeoEntityRenderer<ZombieHumanEntity, LivingEntityRenderState> {
	public ZombieHumanRenderer(EntityRendererProvider.Context context) {
		super(context, new ZombieHumanModel());
	}
}
