package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.ClientAttackCommit;
import com.hrtq.grandcraft.client.ClientCombatPhases;
import com.hrtq.grandcraft.client.ClientStamina;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Swallows an attack input thrown while the player is already mid-swing, so a click
 * during a wind-up or an endlag does <strong>nothing at all</strong>.
 *
 * <p>The server has always refused those swings — {@code canStartAttack} does it, and it
 * is the thing that actually enforces the rule. What it could not do is stop the client
 * from playing vanilla's arm swing on the way to being refused, which read as the player
 * attacking twice and only being credited once. A phantom.
 *
 * <p>Cancelling at HEAD takes the whole input: no swing animation, no
 * {@code ServerboundAttackPacket}, no {@code AttackMissPayload}, and no {@code missTime}.
 * The click is not delayed or queued — it is dropped, which is what makes a committed
 * swing a commitment rather than a suggestion.
 *
 * <h2>Read from the phase, not from a new packet</h2>
 *
 * {@code ClientCombatPhases} already knows: {@code CombatController.syncPhase} sends the
 * phase to every viewer <em>and to the actor itself</em>, precisely so a player can see
 * their own commitment. So the lockout is the phase, exactly, with no second clock to
 * drift against it — unlike {@code ClientAttackLockout}, which carries only the current
 * phase's remaining ticks and would expire three times during one swing.
 *
 * <p><strong>The round trip used to be a hole and no longer is.</strong> The phase for a
 * swing cannot arrive until that swing has been to the server and back, and the first
 * version reasoned that the gap was shorter than a human double-click. It is not — a
 * spam-clicking player lands several clicks inside it, each one passing this check,
 * swinging vanilla's arm, and then being refused. That was the reported phantom. The gap
 * is now covered by {@link ClientAttackCommit}, which the client latches the instant it
 * sends an attack and drops as soon as the server answers.
 *
 * <h2>Every click, including one aimed at a block</h2>
 *
 * The first version let a click through when the crosshair was on a <strong>block</strong>,
 * reasoning that mining is not attacking and an endlag has no business stopping someone
 * breaking stone. <strong>That carve-out was the whole lockout's undoing and was removed
 * the same day.</strong> Blocks are what most of the world is made of — the ground, a
 * wall, anything the player happens to be facing — so in ordinary play the crosshair is on
 * one far more often than not, and the exception was swallowing the rule. The lockout only
 * ever applied when the player happened to be aiming at a mob or at open sky, which read
 * as no lockout at all.
 *
 * <p>What it costs is small and bounded: a player cannot break a block during the few
 * ticks of an endlag. <strong>Mining by holding the button is covered too, and this class
 * cannot do it</strong> — a held button runs {@code Minecraft.continueAttack}, which never
 * comes back through here and swings the arm itself.
 * {@code MinecraftContinueAttackMixin} is the other half.</p>
 *
 * <p><strong>A guard is not covered and must not be</strong>, even though the server also
 * refuses a swing from one. Dropping a guard and attacking in one motion is a deliberate
 * mechanic, and {@code MinecraftAttackGuardMixin} exists to make that exact input work —
 * swallowing it here would undo it.</p>
 *
 * <h2>Two reasons to swallow a click, and they are not the same rule</h2>
 *
 * <p>Mid-swing, <strong>everything</strong> goes — including a click aimed at a block,
 * because a commitment applies to whatever the player might do with that button. Out of
 * <strong>stamina</strong>, only the attack goes: mining costs nothing, and an exhausted
 * player must still be able to swing a pickaxe. The two conditions are written separately
 * for that reason, and the block carve-out belongs to exactly one of them.
 *
 * <p>The stamina half is a prediction of a refusal, not an enforcement — the server
 * refuses these anyway, at {@code canStartAttack}. What it buys is that the refusal costs
 * nothing: no packet, and no commit latch swallowing the next quarter second of input for
 * a swing that was never going to happen. <strong>A swing refused for any other reason
 * still reaches the server, and shows nothing</strong>, because
 * {@code MinecraftAttackSwingMixin} no longer lets the client draw an attack the server
 * has not agreed to.
 *
 * <h2>Third of three at this HEAD</h2>
 *
 * {@code MinecraftAttackGuardMixin} and {@code MinecraftRadialAttackMixin} also inject at
 * the head of {@code startAttack}. Mixin checks for cancellation after <em>each</em>
 * handler, so whichever cancels first stops the rest — and the order is the order the
 * mixins are listed in {@code grandcraft.client.mixins.json}, where the guard comes first.
 *
 * <p>That order is the safe one and worth keeping: the guard's handler sends a release and
 * does not cancel, so it should run before anything that might swallow the click. It is
 * harmless if it ever does not — a player mid-swing cannot be guarding, the two states are
 * exclusive — but the two orderings are not equally obvious and the config decides it.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftAttackLockoutMixin {
	@Shadow
	public LocalPlayer player;

	@Shadow
	public HitResult hitResult;

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void grandcraft$dropAttackWhileSwinging(CallbackInfoReturnable<Boolean> info) {
		if (this.player == null) {
			return;
		}

		long now = Util.getMillis();

		// The phase is the authority; the commit covers the interval before the first one
		// can possibly arrive. See ClientAttackCommit.
		if (ClientCombatPhases.stateOf(this.player.getId(), now).isAttack()
				|| ClientAttackCommit.pending(now)) {
			// False is "no block was destroyed", which is the truth: nothing happened.
			info.setReturnValue(false);
			return;
		}

		// Exhausted: the server is going to refuse this swing, so do not make it. Saves
		// the packet and, more usefully, saves the commit latch below from swallowing the
		// next quarter second of input for an attack that was never going to happen.
		//
		// NOTE THE CARVE-OUT, and that it is the opposite of the rule above: a click
		// aimed at a block is let through, because mining costs no stamina and an
		// exhausted player must still be able to swing a pickaxe. The same carve-out on
		// the mid-swing lockout was its undoing and was reverted the same day — there it
		// is wrong, because a commitment applies to everything the player might do with
		// that button; here it is right, because empty stamina only forbids the attack.
		if (ClientStamina.exhausted() && !aimingAtBlock()) {
			info.setReturnValue(false);
		}
	}

	/**
	 * Whether this click would start mining rather than attacking.
	 *
	 * <p>A null hit result counts as not a block: {@code startAttack} returns early on one
	 * anyway, so the answer only has to be safe, and refusing is the safe direction.
	 */
	private boolean aimingAtBlock() {
		return this.hitResult != null && this.hitResult.getType() == HitResult.Type.BLOCK;
	}
}
