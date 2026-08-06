package com.hrtq.grandcraft.client.hud;

import com.hrtq.grandcraft.GrandCraft;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the quick-slot wheel. {@link RadialMenu} owns where the wedges are and what
 * they mean; this owns what they look like.
 *
 * <h2>The artist's ring draws the boxes</h2>
 *
 * The wheel used to be twelve hand-coded boxes on a plain fill. It is now one blit of
 * {@link #WHEEL} — the delivered artwork, cropped to its own alpha bounds — and the
 * twelve slots are <em>painted into it</em>. Nothing here draws a box any more; the
 * only rectangles left are the three state overlays (highlighted, held, locked), which
 * go on top of the painted box rather than replacing it.
 *
 * <p>That makes the geometry the artwork's rather than this file's, so it is measured
 * off the artwork and derived rather than typed. In the 1037x955 crop the painted boxes
 * sit on a circle of radius 328 and are about 93 across, and the crop's centre is the
 * ring's centre to the pixel — hence {@link #RADIUS} and {@link #BOX} as fractions of
 * {@link #WHEEL_WIDTH}. <strong>{@code WHEEL_WIDTH} is therefore the one number to
 * change if the wheel is the wrong size</strong>: the artwork, the ring and the item
 * boxes all follow it together and cannot drift apart.
 *
 * <p>The twelve boxes are on the ideal thirty-degree grid to within eight source pixels,
 * so wedge 0 lands on the top box with no per-wedge correction. That is checked, not
 * assumed — the alternative was a table of twelve offsets that no future re-export
 * would survive.
 *
 * <h2>The inventory wedge</h2>
 *
 * The one wedge that is a button rather than a slot, so it is the one wedge whose
 * <em>contents</em> are a texture instead of an item: the authored icon at
 * {@link #INVENTORY_ICON}, blitted into the same 16x16 the items get so the ring reads
 * as one row of equal things. It carries no count and no durability bar for the same
 * reason — it is not a stack the player owns.
 *
 * <h2>Fade in, but not out</h2>
 *
 * Opening ramps the wheel's alpha over a few frames so it does not appear as a hard
 * flash in the middle of the screen. Closing is immediate and deliberately so: the
 * player has just chosen something, and the answer to "what am I holding" is the item
 * name and the hand, not a wheel still dissolving over them.
 *
 * <p>The ring rides that same fade, multiplied by {@link #WHEEL_OPACITY} so it settles
 * slightly transparent rather than solid.
 */
public final class RadialMenuElement implements HudElement {
	public static final Identifier ID = GrandCraft.id("radial_menu");

	/** The ring itself: the artist's wheel, cropped to its alpha bounds. */
	private static final Identifier WHEEL =
			GrandCraft.id("textures/gui/radial/wheel.png");

	/** The inventory button's icon, named in exactly one place. */
	private static final Identifier INVENTORY_ICON =
			GrandCraft.id("textures/gui/radial/inventory.png");

	/**
	 * The source size of the cropped artwork. These set the aspect ratio and nothing
	 * else — the drawn height is derived from them and never typed, or the ring would
	 * stretch into an ellipse and the painted boxes would leave the circle the item
	 * positions are computed on.
	 */
	private static final int TEXTURE_WIDTH = 1037;
	private static final int TEXTURE_HEIGHT = 955;

	/**
	 * How wide the wheel draws. <strong>The single number to change if it is too big or
	 * too small</strong> — the height, the ring radius and the slot boxes all follow.
	 *
	 * <p>Chosen so the painted boxes come out 20 pixels, which leaves a 16x16 item two
	 * pixels of margin inside one. Note that at GUI scale 4 the artwork's lower spikes
	 * reach into the ability bar's row; lowering this is the fix if that reads badly.
	 */
	private static final int WHEEL_WIDTH = 224;

	/** Derived, never typed: a hand-written height would distort the ring. */
	private static final int WHEEL_HEIGHT =
			Math.round((float) WHEEL_WIDTH * TEXTURE_HEIGHT / TEXTURE_WIDTH);

	/**
	 * How faded the ring draws, before the open fade is applied on top. The user asked
	 * for slightly transparent — enough that the world reads through it and it sits over
	 * the scene rather than replacing it.
	 *
	 * <p><strong>The only value involved if it is too solid or too ghostly.</strong>
	 */
	private static final float WHEEL_OPACITY = 0.85F;

	/**
	 * Distance from the screen centre to the middle of a slot box, and the box's own
	 * size. Both are measured off the artwork — the painted ring is at 328/1037 of the
	 * crop's width and a painted box is about 93/1037 across — so they move with
	 * {@link #WHEEL_WIDTH} instead of having to be re-typed alongside it.
	 */
	private static final int RADIUS = Math.round(WHEEL_WIDTH * 0.3163F);
	private static final int BOX = Math.round(WHEEL_WIDTH * 0.0897F);

	/** An item sprite is 16x16 whatever the wheel does, so this is not derived. */
	private static final int ITEM = 16;

	/**
	 * The three state overlays, drawn over the painted box and under the item.
	 *
	 * <p>The highlight is a translucent wash rather than the old opaque border: the art
	 * already draws a frame around every box, and a second one over it read as a box
	 * inside a box. A wash also leaves the item on top legible, which an opaque fill
	 * would not.
	 */
	private static final int COLOUR_HIGHLIGHT = 0x554C7DF0;
	private static final int COLOUR_HELD = 0xFF7A7AA8;
	private static final int COLOUR_LOCKED = 0xB0121220;
	private static final int COLOUR_NAME = 0xFFFFFFFF;

	/** The aim dot, which keeps the solid highlight colour it always had. */
	private static final int COLOUR_AIM_LIVE = 0xFF4C7DF0;

	/** The aim dot inside the dead zone: present, but visibly not choosing anything. */
	private static final int COLOUR_AIM_IDLE = 0x90707090;

	/** Half the aim dot's width. Small — it is a pointer, not a cursor. */
	private static final int AIM_DOT = 2;

	/** Clear of the artwork, which is what the name is measured against now. */
	private static final int NAME_GAP = 6;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (!RadialMenu.isOpen()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;

		if (player == null) {
			return;
		}

		float fade = RadialMenu.openFraction(Util.getMillis());
		int centreX = graphics.guiWidth() / 2;
		int centreY = graphics.guiHeight() / 2;
		int highlighted = RadialMenu.highlightedWedge();
		int held = player.getInventory().getSelectedSlot();

		extractRing(graphics, centreX, centreY, fade);

		for (int wedge = 0; wedge < RadialMenu.WEDGES; wedge++) {
			int slot = RadialMenu.slotForWedge(wedge);
			boolean inventoryWedge = wedge == RadialMenu.INVENTORY_WEDGE;
			boolean locked = !inventoryWedge && slot == RadialMenu.NO_WEDGE;

			int left = centreX + offsetX(wedge) - BOX / 2;
			int top = centreY + offsetY(wedge) - BOX / 2;

			if (locked) {
				// Nothing reaches this now that the held range is eleven and the art
				// paints twelve boxes. It stays because the alternative is a wedge with
				// a painted box and no answer: if the range is ever narrowed again, a
				// darkened box says "not yours" where a bare one would say "empty".
				graphics.fill(left, top, left + BOX, top + BOX, fade(COLOUR_LOCKED, fade));
				continue;
			}

			if (wedge == highlighted) {
				graphics.fill(left, top, left + BOX, top + BOX, fade(COLOUR_HIGHLIGHT, fade));
			} else if (!inventoryWedge && slot == held) {
				extractOutline(graphics, left, top, fade(COLOUR_HELD, fade));
			}

			int itemX = left + (BOX - ITEM) / 2;
			int itemY = top + (BOX - ITEM) / 2;

			if (inventoryWedge) {
				// The whole texture into the item's box: u and v zero and the source size
				// given as the destination size, which asks for the sprite scaled to fit
				// rather than a region cut out of it.
				graphics.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_ICON,
						itemX, itemY, 0.0F, 0.0F, ITEM, ITEM, ITEM, ITEM);
				continue;
			}

			// Items ignore the fade: the extract pipeline draws them through the item
			// renderer rather than as tinted quads, and half of a fade this short is a
			// single frame. They appear with the boxes and that is close enough.
			ItemStack stack = player.getInventory().getItem(slot);

			if (stack.isEmpty()) {
				continue;
			}

			graphics.item(player, stack, itemX, itemY, wedge);
			graphics.itemDecorations(client.font, stack, itemX, itemY);
		}

		extractAimMarker(graphics, centreX, centreY, fade);
		extractName(graphics, client, player, centreX, centreY, highlighted);
	}

	/**
	 * The ring, centred on the crosshair and slightly transparent.
	 *
	 * <p>Faded through the blit's trailing colour rather than through the texture's own
	 * alpha channel, which is what lets the opening fade and {@link #WHEEL_OPACITY}
	 * multiply into one value — and keeps the shipped PNG the artwork as delivered.
	 * {@code GUI_TEXTURED} blends translucent, so the alpha byte there does what it
	 * looks like it does.
	 */
	private static void extractRing(GuiGraphicsExtractor graphics,
			int centreX, int centreY, float fade) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, WHEEL,
				centreX - WHEEL_WIDTH / 2, centreY - WHEEL_HEIGHT / 2, 0.0F, 0.0F,
				WHEEL_WIDTH, WHEEL_HEIGHT, WHEEL_WIDTH, WHEEL_HEIGHT,
				fade(0xFFFFFFFF, fade * WHEEL_OPACITY));
	}

	/**
	 * A one-pixel frame around a slot box, drawn as four spans.
	 *
	 * <p>An outline rather than the old two-fill border-plus-inside idiom, because the
	 * inside is the artwork now and painting over it would hide the box this is meant to
	 * point at.
	 */
	private static void extractOutline(GuiGraphicsExtractor graphics,
			int left, int top, int colour) {
		int right = left + BOX;
		int bottom = top + BOX;

		graphics.fill(left, top, right, top + 1, colour);
		graphics.fill(left, bottom - 1, right, bottom, colour);
		graphics.fill(left, top + 1, left + 1, bottom - 1, colour);
		graphics.fill(right - 1, top + 1, right, bottom - 1, colour);
	}

	/**
	 * A dot showing where the aim actually is.
	 *
	 * <p>Without it the wheel gives no answer to "why is nothing highlighted" — the
	 * player is inside the dead zone and has no way to know it. The dot is dimmed while
	 * it is in there, which makes the dead zone a thing you can see rather than a
	 * threshold you have to infer.
	 */
	private static void extractAimMarker(GuiGraphicsExtractor graphics,
			int centreX, int centreY, float fade) {
		int x = centreX + Math.round((float) (RadialMenu.aimFractionX() * RADIUS));
		int y = centreY + Math.round((float) (RadialMenu.aimFractionY() * RADIUS));
		int colour = RadialMenu.aimIsLive() ? COLOUR_AIM_LIVE : COLOUR_AIM_IDLE;

		graphics.fill(x - AIM_DOT, y - AIM_DOT, x + AIM_DOT, y + AIM_DOT, fade(colour, fade));
	}

	/**
	 * Names what is aimed at, under the ring. Nothing is drawn when the aim is in the
	 * dead zone — an empty line there would read as "this slot is empty" rather than
	 * "you are not pointing at anything".
	 *
	 * <p>Measured against the artwork's own bottom edge rather than against the slot
	 * ring: the wheel's rim and spikes now reach well past the boxes, and a line placed
	 * off the ring would be painted across them.
	 */
	private static void extractName(GuiGraphicsExtractor graphics, Minecraft client,
			LocalPlayer player, int centreX, int centreY, int highlighted) {
		if (highlighted == RadialMenu.NO_WEDGE) {
			return;
		}

		int y = centreY + WHEEL_HEIGHT / 2 + NAME_GAP;

		if (highlighted == RadialMenu.INVENTORY_WEDGE) {
			graphics.centeredText(client.font,
					Component.translatable("screen.grandcraft.radial.inventory"),
					centreX, y, COLOUR_NAME);
			return;
		}

		int slot = RadialMenu.slotForWedge(highlighted);

		if (slot == RadialMenu.NO_WEDGE) {
			return;
		}

		ItemStack stack = player.getInventory().getItem(slot);

		if (!stack.isEmpty()) {
			graphics.centeredText(client.font, stack.getHoverName(), centreX, y, COLOUR_NAME);
		}
	}

	/** Wedge 0 is up and they run clockwise, which is what puts the button at six. */
	private static int offsetX(int wedge) {
		return Math.round((float) (Math.sin(angle(wedge)) * RADIUS));
	}

	private static int offsetY(int wedge) {
		return Math.round((float) (-Math.cos(angle(wedge)) * RADIUS));
	}

	private static double angle(int wedge) {
		return wedge * (Math.PI * 2.0) / RadialMenu.WEDGES;
	}

	/**
	 * Scales a colour's alpha byte. GUI colours are ARGB and the alpha is mandatory,
	 * so this multiplies the byte that is already there rather than supplying one.
	 */
	private static int fade(int argb, float fraction) {
		int alpha = Math.round((argb >>> 24) * Math.clamp(fraction, 0.0F, 1.0F));
		return (alpha << 24) | (argb & 0x00FFFFFF);
	}
}
