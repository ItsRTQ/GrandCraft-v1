package com.hrtq.grandcraft.mixin;

import com.hrtq.grandcraft.stats.GrandCraftAttributes;
import com.hrtq.grandcraft.stats.StatConstants;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives players the four GrandCraft stats.
 *
 * <p>{@code Player.createAttributes} is a static factory that builds the attribute
 * supplier every player is created from, so appending to the builder it returns is
 * how a new attribute joins the player without touching any entity instance.
 *
 * <p>Everything starts at {@link StatConstants#NEUTRAL}. Real values are written as
 * base values by {@code PlayerStats.applyBaselines} once the player's class is
 * known, which cannot happen here — this runs once, for the type, before any player
 * exists.
 *
 * <p>Only players get these. See {@link GrandCraftAttributes} for why, and for the
 * consequence: any read of a stat has to cope with an entity that has none.
 */
@Mixin(Player.class)
public class PlayerAttributesMixin {
	@Inject(method = "createAttributes", at = @At("RETURN"))
	private static void grandcraft$addStats(CallbackInfoReturnable<AttributeSupplier.Builder> info) {
		info.getReturnValue()
				.add(GrandCraftAttributes.STRENGTH, StatConstants.NEUTRAL)
				.add(GrandCraftAttributes.AGILITY, StatConstants.NEUTRAL)
				.add(GrandCraftAttributes.CONSTITUTION, StatConstants.NEUTRAL)
				.add(GrandCraftAttributes.ARCANE, StatConstants.NEUTRAL);
	}
}
