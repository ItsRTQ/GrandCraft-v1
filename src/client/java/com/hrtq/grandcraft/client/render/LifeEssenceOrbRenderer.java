package com.hrtq.grandcraft.client.render;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.entity.LifeEssenceOrb;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Draws a Life Essence Orb as a camera-facing quad cut from the artist's sheet.
 *
 * <p>Structurally vanilla's {@code ExperienceOrbRenderer}: same billboard, same
 * scale, same gentle bob, so an Essence orb sits in the world exactly the way a
 * player already expects a collectable orb to.
 *
 * <p>The sheet is a <strong>4x4 grid</strong>, which is vanilla's own layout — its
 * {@code experience_orb.png} is 64x64 of 16x16 cells. The artist's is the same grid
 * at sixteen times the resolution, so the cell arithmetic below is written as
 * fractions of the grid rather than in pixels and is resolution-independent.
 *
 * <p><strong>One deliberate departure: no colour cycling.</strong> Vanilla drives its
 * red and blue channels from a sine and pins green at full, which is what makes its
 * orb green — sensible when the texture is a pale blob, wrong here. Essence orbs are
 * authored red and orange, so tinting is kept neutral and only the brightness
 * breathes. Alpha is left at full for the same reason: the texture supplies its own
 * soft edge, and vanilla's half-transparency would wash the art out.
 */
public class LifeEssenceOrbRenderer extends EntityRenderer<LifeEssenceOrb, LifeEssenceOrbRenderState> {
	private static final Identifier TEXTURE = GrandCraft.id("textures/entity/life_essence_orb.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entityTranslucentCullItemTarget(TEXTURE);

	/** Cells per row and per column on the sheet. */
	private static final int GRID = 4;

	/** Vanilla's own orb scale, so the two read as the same size of thing. */
	private static final float SCALE = 0.3F;

	/** Lifts the quad off the ground so a resting orb is not half sunk into the block. */
	private static final float GROUND_CLEARANCE = 0.1F;

	/** How much of the light an orb supplies itself, on top of the block light. */
	private static final int GLOW = 7;

	/** How fast the brightness breathes, in ticks per radian. */
	private static final float PULSE_SPEED = 2.0F;

	/** The dimmest and brightest the tint goes; never dark, only less bright. */
	private static final int PULSE_FLOOR = 200;
	private static final int PULSE_RANGE = 55;

	public LifeEssenceOrbRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public LifeEssenceOrbRenderState createRenderState() {
		return new LifeEssenceOrbRenderState();
	}

	@Override
	public void extractRenderState(LifeEssenceOrb orb, LifeEssenceOrbRenderState state, float partialTick) {
		super.extractRenderState(orb, state, partialTick);
		state.icon = orb.getIcon();
	}

	/**
	 * Orbs glow a little in the dark, as vanilla's do — otherwise one dropped in a
	 * cave is invisible until the player is on top of it.
	 */
	@Override
	protected int getBlockLightLevel(LifeEssenceOrb orb, BlockPos pos) {
		return Mth.clamp(super.getBlockLightLevel(orb, pos) + GLOW, 0, 15);
	}

	@Override
	public void submit(LifeEssenceOrbRenderState state, PoseStack poseStack,
			SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();

		int icon = state.icon;
		float u0 = (icon % GRID) / (float) GRID;
		float u1 = u0 + 1.0F / GRID;
		float v0 = (icon / GRID) / (float) GRID;
		float v1 = v0 + 1.0F / GRID;

		int tint = PULSE_FLOOR + (int) ((Mth.sin(state.ageInTicks / PULSE_SPEED) + 1.0F)
				* 0.5F * PULSE_RANGE);

		poseStack.translate(0.0F, GROUND_CLEARANCE, 0.0F);
		poseStack.mulPose(camera.orientation);
		poseStack.scale(SCALE, SCALE, SCALE);

		int light = state.lightCoords;

		collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, consumer) -> {
			// Wound anticlockwise from the bottom-left, matching vanilla's quad, with
			// the sheet's v axis running downward — so v1 belongs to the lower edge.
			vertex(consumer, pose, -0.5F, -0.25F, tint, u0, v1, light);
			vertex(consumer, pose, 0.5F, -0.25F, tint, u1, v1, light);
			vertex(consumer, pose, 0.5F, 0.75F, tint, u1, v0, light);
			vertex(consumer, pose, -0.5F, 0.75F, tint, u0, v0, light);
		});

		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y,
			int tint, float u, float v, int light) {
		consumer.addVertex(pose, x, y, 0.0F)
				.setColor(tint, tint, tint, 255)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}
}
