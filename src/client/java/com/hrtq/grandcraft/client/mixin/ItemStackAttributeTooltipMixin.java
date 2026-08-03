package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.tooltip.WeaponTooltip;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes vanilla's "7 Attack Damage / 1.6 Attack Speed" block from anything the mod
 * now describes itself.
 *
 * <p>Not tidying. Both of those lines are actively false after the damage overhaul: the
 * damage figure is a number nothing reads any more — the real one depends on who is
 * holding the weapon — and attack speed no longer scales damage at all now that
 * {@code PlayerAttackMixin} has deleted the cooldown curve, so it describes a pacing
 * system that has been replaced by endlag and stamina. Leaving them would put two
 * confident wrong numbers directly above one right one.
 *
 * <p><strong>Scoped to weapons on purpose.</strong> The cancel takes out every attribute
 * line on the stack, so armour has to keep reaching vanilla or a chestplate would stop
 * reporting its own armour points. {@link WeaponTooltip#describes} is the single test
 * both sides use, which is what stops the block being removed from something nothing
 * replaces it on.
 *
 * <p>Client-only: this is drawn, never simulated.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackAttributeTooltipMixin {

	@Inject(
			method = "addAttributeTooltips(Ljava/util/function/Consumer;"
					+ "Lnet/minecraft/world/item/component/TooltipDisplay;"
					+ "Lnet/minecraft/world/entity/player/Player;)V",
			at = @At("HEAD"), cancellable = true)
	private void grandcraft$replaceWeaponAttributes(Consumer<Component> lines,
			TooltipDisplay display, Player player, CallbackInfo info) {
		if (WeaponTooltip.describes((ItemStack) (Object) this)) {
			info.cancel();
		}
	}
}
