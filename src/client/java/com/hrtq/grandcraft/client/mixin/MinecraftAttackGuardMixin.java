package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.ClientGuard;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes sure a guard release reaches the server before the attack that follows it.
 *
 * <p>A raised guard refuses an attack on purpose: dropping it is a decision the
 * player has to time, and that is where the skill in "block, then punish" lives. But
 * {@code ClientGuard.tick} runs at the <em>end</em> of the client tick — which is
 * what leaves the initial right-click to vanilla — while {@code handleKeybinds} runs
 * earlier in the same tick. So releasing and clicking in one motion put the attack
 * packet on the wire ahead of the release, the server refused a swing from a guard
 * it still believed was up, and the click was swallowed. The player saw a delay that
 * was really a lost input.
 *
 * <p>HEAD rather than TAIL, unlike {@code MinecraftAttackMissMixin} on the same
 * method: the point is to get the release out <em>before</em> anything else this
 * method does, and unlike a miss report it does not matter whether a swing actually
 * happens. If one of vanilla's five early guards bails out, the player still let go
 * of the button, so the release is still correct to send.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftAttackGuardMixin {
	@Inject(method = "startAttack", at = @At("HEAD"))
	private void grandcraft$releaseGuardBeforeAttacking(CallbackInfoReturnable<Boolean> info) {
		ClientGuard.releaseBeforeAttack((Minecraft) (Object) this);
	}
}
