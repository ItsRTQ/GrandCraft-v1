package com.hrtq.grandcraft.client.render;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.entity.DemonSkeletonEntity;
import net.minecraft.resources.Identifier;

/**
 * Points GeckoLib at the demonic skeleton's Blockbench files.
 *
 * <p>The identifiers are <strong>bare names</strong>, for the reason spelled out in
 * {@link ZombieHumanModel}: GeckoLib 5 scans {@code assets/<ns>/geckolib/models} and
 * {@code assets/<ns>/geckolib/animations} and keys its cache by the path with that
 * folder prefix and the file extension stripped off. Asking for the full path logs
 * "Superfluous prefix or suffix" and then fails to find it, and a missing animation
 * crashes the render thread on {@code List.getLast()} rather than reporting a missing
 * resource. The texture is exempt — that is an ordinary Minecraft texture path.
 */
public class DemonSkeletonModel extends GeoModel<DemonSkeletonEntity> {
	private static final Identifier MODEL = GrandCraft.id("demon_skeleton");
	private static final Identifier TEXTURE = GrandCraft.id("textures/entity/demon_skeleton.png");
	private static final Identifier ANIMATIONS = GrandCraft.id("demon_skeleton");

	@Override
	public Identifier getModelResource(GeoRenderState renderState) {
		return MODEL;
	}

	@Override
	public Identifier getTextureResource(GeoRenderState renderState) {
		return TEXTURE;
	}

	/** Takes the entity, not {@code GeoAnimatable} — see {@link ZombieHumanModel}. */
	@Override
	public Identifier getAnimationResource(DemonSkeletonEntity animatable) {
		return ANIMATIONS;
	}
}
