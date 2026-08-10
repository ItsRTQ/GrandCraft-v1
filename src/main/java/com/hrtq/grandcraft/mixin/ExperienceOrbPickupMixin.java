package com.hrtq.grandcraft.mixin;

import com.hrtq.grandcraft.combat.Downed;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The twin of {@code ItemEntityPickupMixin}, for experience.
 *
 * <p>A separate class because they are separate entity types with no shared
 * supertype worth targeting, not because the rule differs — it is the same rule.
 *
 * <p>Worth having rather than an afterthought: orbs home in on a nearby player of
 * their own accord, so a downed player would collect the experience from the mob
 * that just downed them without moving a muscle.
 */
@Mixin(ExperienceOrb.class)
public class ExperienceOrbPickupMixin {
	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void grandcraft$noPickupWhileDowned(Player player, CallbackInfo info) {
		if (Downed.isDowned(player)) {
			info.cancel();
		}
	}
}
