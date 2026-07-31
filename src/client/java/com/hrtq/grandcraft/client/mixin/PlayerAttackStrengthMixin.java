package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.ClientAttackLockout;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the vanilla attack indicator show GrandCraft's attack lockout as well as
 * the weapon cooldown, so it tells the player when they can actually swing.
 *
 * <p>Reuses the indicator rather than adding a HUD element: players already read
 * it, and vanilla draws the bar whenever this value is below 1. Only the "ready"
 * flash needs a slow weapon — the progress fill works bare-handed too.
 *
 * <p>The client's value is replaced outright: the lockout when one is running, and
 * full otherwise. Combining it with vanilla's does not work, in either direction —
 * vanilla resets its ticker on every click including misses, and its cooldown is
 * much longer than a short endlag, so whichever way the two are merged the bar
 * spends most of its time reporting the weapon recharging rather than whether the
 * player may act. A lockout begins only when an attack lands or the player is hit,
 * which is exactly the question the indicator is being asked here.
 *
 * <p><strong>This decouples the indicator from vanilla's damage scaling.</strong>
 * {@code Player.attack} still scales damage by the real value on the server, so a
 * swing during the weapon's cooldown deals less damage than a full one without the
 * bar warning about it. Making the two agree again means neutralising vanilla's
 * cooldown so GrandCraft's endlag is the only thing pacing a player — a deliberate
 * design step rather than something to do quietly.
 *
 * <h2>Why the guards matter</h2>
 * {@code getAttackStrengthScale} also scales damage inside {@code Player.attack},
 * which runs on the server — and in single player the integrated server shares
 * these classes with the client. Restricting the override to a client-side
 * {@code Level}, and to the local player, keeps it to the HUD. The client's only
 * other reader of this value is the HUD itself, so nothing else is affected.
 */
@Mixin(Player.class)
public abstract class PlayerAttackStrengthMixin {
	@Inject(method = "getAttackStrengthScale", at = @At("RETURN"), cancellable = true)
	private void grandcraft$showLockout(float partialTick, CallbackInfoReturnable<Float> info) {
		Player self = (Player) (Object) this;

		if (!self.level().isClientSide()) {
			return;
		}

		if (self != Minecraft.getInstance().player) {
			return;
		}

		Float progress = ClientAttackLockout.progress(Util.getMillis());

		// Full whenever no lockout is running. Falling through to vanilla here was
		// the bug: its cooldown is far longer than a short endlag, so most of the
		// time the bar was showing vanilla recharging rather than anything about
		// whether the player may act.
		info.setReturnValue(progress == null ? 1.0F : progress);
	}
}
