package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.ClientAttackCommit;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops the client drawing the hit before the server has dealt it.
 *
 * <p><strong>This is the file to delete if hits stop appearing at all.</strong> It is
 * the client half of player attack phases, and it does exactly one thing: it takes away
 * vanilla's optimistic prediction.
 *
 * <h2>What vanilla does, and why it cannot stay</h2>
 *
 * {@code MultiPlayerGameMode.attack} is three statements — send
 * {@code ServerboundAttackPacket}, call {@code Player.attack} <em>locally</em>, reset the
 * attack-strength ticker. That middle call is a prediction: it runs the whole damage path
 * on the client so the hurt flash, the knockback and the particles appear on the click
 * rather than a round trip later.
 *
 * <p>That is right when the server also deals damage on the click, and it was, until
 * 2026-08-05. Now the server books a wind-up and lands the blow several ticks later, so
 * the prediction is a hit drawn at a time when nothing has happened — followed by the
 * real one when it does. Two flashes, two knockbacks, and a telegraph whose whole point
 * is undone by showing its result before it plays.
 *
 * <p>The packet still goes, and the ticker is still reset. Only the local damage is
 * dropped, so the client waits to be told — which for a mod whose combat is entirely
 * server-authoritative is what it should have been doing anyway.
 *
 * <h2>Why a redirect is safe here</h2>
 *
 * {@code Player.attack} is invoked <strong>exactly once</strong> in
 * {@code MultiPlayerGameMode.attack}, confirmed by disassembly. That is the same
 * property that makes {@code PlayerAttackMixin}'s three redirects safe, and the same
 * thing to re-check after a Minecraft update: a second call site would silently take
 * this one's place.
 *
 * <p>Unconditional rather than gated on a combat profile. The player always has
 * {@code PHASED_MELEE} now, so there is no case where the prediction is wanted; and
 * asking the profile layer from a client thread is exactly what
 * {@code MeleeDamage.appliesTo} refuses to do, for the race it would open with
 * {@code CombatProfiles.rebuildIfStale}.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeAttackMixin {
	/**
	 * Latches the client's own commitment, because this is the moment the attack packet
	 * goes out — everything after it is the server's to answer, and until it does the
	 * client must stop accepting attack input. See {@link ClientAttackCommit} for why the
	 * phase alone cannot cover that interval.
	 *
	 * <p>At HEAD rather than beside the redirect below: the point is that the request has
	 * been made, which is true whether or not vanilla would have predicted a hit.
	 */
	@Inject(method = "attack", at = @At("HEAD"))
	private void grandcraft$commitAttack(Player player, Entity target, CallbackInfo info) {
		ClientAttackCommit.commit(Util.getMillis());
	}

	@Redirect(
			method = "attack",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Player;"
							+ "attack(Lnet/minecraft/world/entity/Entity;)V"))
	private void grandcraft$noHitPrediction(Player player, Entity target) {
		// Deliberately empty. The server will say when this landed.
	}
}
