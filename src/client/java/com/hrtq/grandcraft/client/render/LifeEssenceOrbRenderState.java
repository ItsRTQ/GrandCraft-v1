package com.hrtq.grandcraft.client.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * What the renderer needs to draw one Life Essence Orb.
 *
 * <p>Only the icon: position, age and light already ride on the base state, and the
 * orb's whole appearance beyond those is which cell of the sheet it uses.
 */
public class LifeEssenceOrbRenderState extends EntityRenderState {
	/** Index into the texture's grid, chosen from the orb's Essence value. */
	public int icon;
}
