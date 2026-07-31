package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.ClientGuard;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops a held guard from also using the item behind it.
 *
 * <p>{@code handleKeybinds} reaches {@code startUseItem} twice: once on the press,
 * and again on a four tick repeat for as long as the button is down. Only the second
 * is cancelled here, because {@link ClientGuard} does not latch the hold until the
 * end of the tick the press arrived on — so the press itself still opens the chest,
 * toggles the door or places the block, and only the repeat belongs to the guard.
 *
 * <p>Without this, holding the guard on a stack of blocks in the off-hand would build
 * a tower while blocking.
 */
@Mixin(Minecraft.class)
public class MinecraftUseItemMixin {
	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void grandcraft$guardOwnsHeldUse(CallbackInfo info) {
		if (ClientGuard.suppressesItemUse()) {
			info.cancel();
		}
	}
}
