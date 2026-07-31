package com.hrtq.grandcraft.mixin;

import com.hrtq.grandcraft.combat.CombatConstants;
import com.hrtq.grandcraft.combat.CombatProfile;
import com.hrtq.grandcraft.combat.CombatProfiles;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes melee reach configurable per {@link com.hrtq.grandcraft.combat.CombatActor}.
 *
 * <p>Vanilla's check is an inflated-hitbox intersection, not a distance compare:
 * <pre>
 * AttackRange range = getActiveItem().get(DataComponents.ATTACK_RANGE);
 * double max = range == null ? DEFAULT_ATTACK_REACH : range.effectiveMaxRange(this);
 * return getAttackBoundingBox(max).intersects(target.getHitbox()) &amp;&amp; ...min check...;
 * </pre>
 * so the configured value is substituted for {@code DEFAULT_ATTACK_REACH}, keeping
 * the same shape. It is how far the mob's box is <em>inflated</em> horizontally,
 * which means the reach a player experiences also includes half the mob's width
 * and half their own.
 *
 * <p>A held item carrying an {@code ATTACK_RANGE} component is left entirely alone.
 * That component is the item's statement about its own reach, including a minimum
 * range that a plain inflation cannot express, so overriding it would silently
 * break spear-like weapons.
 */
@Mixin(Mob.class)
public abstract class MobMeleeRangeMixin {
	@Shadow
	protected abstract AABB getAttackBoundingBox(double inflation);

	@Inject(method = "isWithinMeleeAttackRange", at = @At("HEAD"), cancellable = true)
	private void grandcraft$configurableReach(LivingEntity target,
			CallbackInfoReturnable<Boolean> info) {
		Mob self = (Mob) (Object) this;

		// Combat is server-authoritative, and the range check also runs client side
		// during rendering-adjacent work; leaving the client on vanilla keeps the two
		// from disagreeing about anything the server has not decided.
		if (self.level().isClientSide()) {
			return;
		}

		CombatProfile profile = CombatProfiles.forEntity(self);

		// No profile, or an actor whose melee is not ours to govern: pure vanilla.
		if (profile == null || !profile.actor().usesMeleeGoal()) {
			return;
		}

		// The item knows its own reach, including a minimum range this cannot model.
		if (self.getActiveItem().has(DataComponents.ATTACK_RANGE)) {
			return;
		}

		AABB hitbox = ((LivingEntityHitboxAccessor) target).grandcraft$getHitbox();
		info.setReturnValue(
				this.getAttackBoundingBox(CombatConstants.MELEE_REACH).intersects(hitbox));
	}
}
