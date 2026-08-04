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
 * <h2>Boxes on a circle, not pie slices</h2>
 *
 * Each wedge is a square slot box placed around a circle rather than a filled arc.
 * That is what the extract pipeline draws cheaply — {@code fill} takes rectangles —
 * and it is also what an item sprite wants to sit in. When the artist delivers a ring
 * texture the boxes can stay exactly where they are underneath it.
 *
 * <h2>The inventory wedge</h2>
 *
 * The one wedge that is a button rather than a slot, so it is the one wedge drawn as a
 * texture instead of through the item renderer: the authored icon at
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
 */
public final class RadialMenuElement implements HudElement {
	public static final Identifier ID = GrandCraft.id("radial_menu");

	/** The inventory button's icon, named in exactly one place. */
	private static final Identifier INVENTORY_ICON =
			GrandCraft.id("textures/gui/radial/inventory.png");

	/** Distance from the screen centre to the middle of a slot box. */
	private static final int RADIUS = 62;

	/** A slot box, and the 16x16 item centred in it. */
	private static final int BOX = 22;
	private static final int ITEM = 16;

	private static final int COLOUR_BOX = 0xB0121220;
	private static final int COLOUR_BORDER = 0xFF2E2E44;
	private static final int COLOUR_HIGHLIGHT = 0xFF4C7DF0;
	private static final int COLOUR_HELD = 0xFF7A7AA8;
	private static final int COLOUR_LOCKED = 0x60121220;
	private static final int COLOUR_NAME = 0xFFFFFFFF;

	/** The aim dot inside the dead zone: present, but visibly not choosing anything. */
	private static final int COLOUR_AIM_IDLE = 0x90707090;

	/** Half the aim dot's width. Small — it is a pointer, not a cursor. */
	private static final int AIM_DOT = 2;

	/** Clear of the ring, and clear of the crosshair the ring surrounds. */
	private static final int NAME_GAP = 14;

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

		for (int wedge = 0; wedge < RadialMenu.WEDGES; wedge++) {
			int slot = RadialMenu.slotForWedge(wedge);
			boolean inventoryWedge = wedge == RadialMenu.INVENTORY_WEDGE;
			boolean locked = !inventoryWedge && slot == RadialMenu.NO_WEDGE;

			int left = centreX + offsetX(wedge) - BOX / 2;
			int top = centreY + offsetY(wedge) - BOX / 2;

			int border = wedge == highlighted
					? COLOUR_HIGHLIGHT
					: (!inventoryWedge && slot == held ? COLOUR_HELD : COLOUR_BORDER);

			graphics.fill(left, top, left + BOX, top + BOX, fade(border, fade));
			graphics.fill(left + 1, top + 1, left + BOX - 1, top + BOX - 1,
					fade(locked ? COLOUR_LOCKED : COLOUR_BOX, fade));

			if (locked) {
				continue;
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
		int colour = RadialMenu.aimIsLive() ? COLOUR_HIGHLIGHT : COLOUR_AIM_IDLE;

		graphics.fill(x - AIM_DOT, y - AIM_DOT, x + AIM_DOT, y + AIM_DOT, fade(colour, fade));
	}

	/**
	 * Names what is aimed at, under the ring. Nothing is drawn when the aim is in the
	 * dead zone — an empty line there would read as "this slot is empty" rather than
	 * "you are not pointing at anything".
	 */
	private static void extractName(GuiGraphicsExtractor graphics, Minecraft client,
			LocalPlayer player, int centreX, int centreY, int highlighted) {
		if (highlighted == RadialMenu.NO_WEDGE) {
			return;
		}

		int y = centreY + RADIUS + BOX / 2 + NAME_GAP;

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
