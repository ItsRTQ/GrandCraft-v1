package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.GrandCraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Puts GrandCraft's title over the main menu in place of the Minecraft logo.
 *
 * <p>The artwork is a logo, not a backdrop — 82% of the delivered image is transparent —
 * so this replaces the <em>logo</em> and leaves vanilla's panorama, buttons and splash
 * exactly as they are. Nothing here touches any other screen.
 *
 * <h2>One call, redirected</h2>
 *
 * {@code TitleScreen.extractRenderState} calls {@code LogoRenderer.extractRenderState}
 * exactly once (verified at offset 132 in the 26.2 bytecode), and that one call draws
 * <em>both</em> vanilla pieces: the 256x44 logo at y=30, and the "Java Edition" strip
 * beneath it. Redirecting it therefore replaces both — deliberately, since an edition
 * strip under someone else's title reads as a leftover.
 *
 * <p>The fade is preserved by passing the alpha straight through to a tinted blit. That
 * alpha already accounts for {@code keepLogoThroughFade}: the title screen resolves it
 * before making this call, so the logo fades in with the menu exactly as vanilla's did.
 *
 * <h2>Placement</h2>
 *
 * Centred at vanilla's logo width and top, so it sits in the slot the eye already expects
 * — {@link #LOGO_WIDTH} is vanilla's 256 and {@link #LOGO_TOP} its y=30. The height is
 * whatever that width implies for the artwork's own 1400x292 proportions (53px), rather
 * than vanilla's 44, so the title is never stretched. **Those two constants are the whole
 * tuning surface** if it wants to sit larger or lower.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {

	private static final Identifier GRANDCRAFT$LOGO =
			GrandCraft.id("textures/gui/title/logo.png");

	/** Vanilla's logo width, so this occupies the same slot on screen. */
	private static final int LOGO_WIDTH = 256;

	/** 1400x292 of artwork at that width, kept in proportion. */
	private static final int LOGO_HEIGHT = 53;

	/** Vanilla's {@code DEFAULT_HEIGHT_OFFSET}. */
	private static final int LOGO_TOP = 30;

	@Redirect(method = "extractRenderState",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/components/LogoRenderer;"
							+ "extractRenderState(Lnet/minecraft/client/gui/"
							+ "GuiGraphicsExtractor;IF)V"))
	private void grandcraft$drawTitle(LogoRenderer renderer, GuiGraphicsExtractor graphics,
			int screenWidth, float alpha) {
		// The whole texture into the box: source offset zero and the source size given as
		// the destination size, the same idiom the mod's other artwork uses.
		graphics.blit(RenderPipelines.GUI_TEXTURED, GRANDCRAFT$LOGO,
				screenWidth / 2 - LOGO_WIDTH / 2, LOGO_TOP, 0.0F, 0.0F,
				LOGO_WIDTH, LOGO_HEIGHT, LOGO_WIDTH, LOGO_HEIGHT, ARGB.white(alpha));
	}
}
