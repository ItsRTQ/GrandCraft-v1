package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.hud.RadialMenu;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a left-click commit the wheel's selection instead of swinging.
 *
 * <p>The wheel can be committed two ways — release the key, or click — because the
 * two suit different hands, and a click that also threw a punch would make the second
 * one useless.
 *
 * <p>The selection is made <em>here</em> rather than by polling the attack key in the
 * client tick, because {@code handleKeybinds} drains that key's clicks itself before
 * the tick ends: anything polling it afterwards would find the press already spent.
 * This is the one place that sees the click before it becomes an attack.
 *
 * <p>Note this is the third HEAD injection on {@code startAttack}, alongside
 * {@code MinecraftAttackGuardMixin} and {@code MinecraftAttackMissMixin}. Mixin does
 * not order them, so a click that commits the wheel may also drop a raised guard —
 * harmless, and the alternative is folding three unrelated concerns into one file.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftRadialAttackMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void grandcraft$commitRadialSelection(CallbackInfoReturnable<Boolean> info) {
		if (RadialMenu.isOpen()) {
			RadialMenu.commit((Minecraft) (Object) this);

			// False is vanilla's own "no swing happened" answer; it is what the method
			// returns whenever an attack is refused.
			info.setReturnValue(false);
		}
	}
}
