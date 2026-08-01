package com.hrtq.grandcraft.client.render;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.entity.ZombieHumanEntity;
import net.minecraft.resources.Identifier;

/**
 * Points GeckoLib at the zombie-human's Blockbench files.
 *
 * <h2>The identifiers are bare names, and that is not a mistake</h2>
 * GeckoLib 5 scans {@code assets/<ns>/geckolib/models} and
 * {@code assets/<ns>/geckolib/animations} — <strong>not</strong> the {@code geo/}
 * and {@code animations/} folders GeckoLib 4 used — and it keys its cache by the
 * path with that folder prefix and the file extension stripped off. So a file at
 * {@code geckolib/models/zombie_human.geo.json} is cached as
 * {@code grandcraft:zombie_human}, and asking for it by its full path logs
 * "Superfluous prefix or suffix" and then fails to find it.
 *
 * <p>Getting this wrong does not degrade gracefully: a missing animation leaves
 * GeckoLib's timeline empty and it crashes the render thread on
 * {@code List.getLast()} rather than reporting a missing resource.
 *
 * <p>The texture is exempt — that is an ordinary Minecraft texture path, loaded by
 * the vanilla texture manager rather than by GeckoLib's cache.
 */
public class ZombieHumanModel extends GeoModel<ZombieHumanEntity> {
	private static final Identifier MODEL = GrandCraft.id("zombie_human");
	private static final Identifier TEXTURE = GrandCraft.id("textures/entity/zombie_human.png");
	private static final Identifier ANIMATIONS = GrandCraft.id("zombie_human");

	@Override
	public Identifier getModelResource(GeoRenderState renderState) {
		return MODEL;
	}

	@Override
	public Identifier getTextureResource(GeoRenderState renderState) {
		return TEXTURE;
	}

	/**
	 * Takes the entity, not {@code GeoAnimatable}: this one is declared on the
	 * generic parameter, unlike the two above which take a render state. The compiled
	 * descriptor erases to {@code GeoAnimatable}, which is misleading if read from a
	 * bytecode dump rather than the generic signature.
	 */
	@Override
	public Identifier getAnimationResource(ZombieHumanEntity animatable) {
		return ANIMATIONS;
	}
}
