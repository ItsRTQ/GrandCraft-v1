package com.hrtq.grandcraft.mixin;

import com.hrtq.grandcraft.combat.CombatProfile;
import com.hrtq.grandcraft.combat.CombatProfiles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes vanilla's blocking mechanic from anyone GrandCraft gives a guard to.
 *
 * <p>{@code applyItemBlocking} is the whole of vanilla blocking in 26.2: it decides
 * how much a held {@code BLOCKS_ATTACKS} item absorbs, and its return value is what
 * {@code hurtServer} then uses to subtract damage, to choose the block sound over
 * the ordinary hurt event, and to decide whether knockback counts as blocked.
 * Returning zero from the top switches all of that off at once.
 *
 * <p>This is removal, not competition. Two blocking systems with different arcs,
 * different costs and different break rules would be impossible to tune and worse to
 * play, so the shield item is kept and its <em>mechanic</em> is replaced: a shield in
 * the off-hand is now what GrandCraft's guard blocks with, and blocks more cheaply
 * with. See {@code CombatController.guardsWithShield}.
 *
 * <p>Scoped to actors that actually have the verb, so anything still on vanilla
 * combat keeps vanilla blocking untouched.
 *
 * <p>Note this method has a second caller — a goat's ram re-invokes it after the
 * damage to decide whether to halve its own knockback. Returning a constant makes
 * that harmless; anything here that charged a resource would be charged twice.
 */
@Mixin(LivingEntity.class)
public class LivingEntityBlockingMixin {
	@Inject(method = "applyItemBlocking", at = @At("HEAD"), cancellable = true)
	private void grandcraft$noVanillaBlocking(ServerLevel level, DamageSource source, float amount,
			CallbackInfoReturnable<Float> info) {
		LivingEntity self = (LivingEntity) (Object) this;
		CombatProfile profile = CombatProfiles.forEntity(self);

		// The verb rather than usesBlock(): an admin who has configured the guard off
		// is turning off GrandCraft's blocking, not turning vanilla's back on.
		if (profile != null && profile.actor().usesBlock()) {
			info.setReturnValue(0.0F);
		}
	}
}
