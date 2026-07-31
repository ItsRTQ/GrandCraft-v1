package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.ClientGameSettings;
import com.hrtq.grandcraft.client.HealthBarTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a recently damaged entity's health as a bar above it.
 *
 * <p>26.2 renders entities by extracting a state object and drawing from that, so
 * there is no {@code render} method to append to. Rather than building billboarded
 * geometry against that pipeline, this borrows the slot vanilla already fills for
 * name tags: setting {@code nameTag} and {@code nameTagAttachment} gets
 * billboarding, depth, scale and distance handling for free.
 *
 * <p>The cost is that a bar replaces a name tag rather than sitting alongside it,
 * for the two seconds it is up. That is a fair trade for not reimplementing the
 * nameplate pipeline, and it is reversible if a textured bar is ever wanted.
 *
 * <p>Injects into the five-argument overload because the three-argument one
 * delegates to it, so this covers renderers that call either.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(
			method = "extractNameTags(Lnet/minecraft/world/entity/Entity;"
					+ "Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V",
			at = @At("TAIL"))
	private void grandcraft$healthBar(Entity entity, EntityRenderState state, float partialTick,
			double nameTagDistance, double scoreDistance, CallbackInfo info) {
		if (!ClientGameSettings.current().healthBars()) {
			return;
		}

		if (!(entity instanceof LivingEntity living)) {
			return;
		}

		// Your own bar would only ever be visible in third person, and would sit in
		// front of the camera the rest of the time.
		if (living == Minecraft.getInstance().player) {
			return;
		}

		if (!HealthBarTracker.isShowing(living, Util.getMillis())) {
			return;
		}

		state.nameTag = HealthBarTracker.bar(living);

		// Vanilla only fills the attachment when it decided to show a name, so it may
		// still be null here even though the tag is now set.
		state.nameTagAttachment = entity.getAttachments()
				.getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTick));
	}
}
