package com.hrtq.grandcraft.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@code LivingEntity.getHitbox()}, which is protected.
 *
 * <p>Needed because {@code MobMeleeRangeMixin} has to test the <em>target's</em>
 * hitbox, and Java's protected access only reaches members through a reference of
 * the accessing class's own type. Using the real {@code getHitbox()} rather than
 * {@code getBoundingBox()} matters: it raises the box for a mounted target, which
 * is exactly what vanilla's range check does.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityHitboxAccessor {
	@Invoker("getHitbox")
	AABB grandcraft$getHitbox();
}
