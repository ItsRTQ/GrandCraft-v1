package com.hrtq.grandcraft.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads {@code Entity.id} without going through {@code getId()}, which throws.
 *
 * <p><strong>{@code getId()} is not safe to call from anything an entity might run
 * while it is still being built.</strong> It is {@code if (this.id == 0) throw new
 * UnsupportedOperationException("Tried to access entity ID before ID assignment")},
 * and the id is only assigned <em>after</em> the constructor returns — so a mixin on
 * anything a constructor reaches must not ask for it. That is not hypothetical: a
 * hitbox override calling it killed the connection the moment a salmon spawned
 * (2026-08-09), because {@code Salmon}'s constructor calls {@code refreshDimensions}
 * and the client builds entities from a packet before assigning their id.
 *
 * <p>Zero is therefore an exact test for "not assigned yet", not a guess: it is the
 * value vanilla's own check compares against, and no real id is ever 0 — the server
 * hands them out from a counter that starts at one and the client uses what it is
 * told.
 *
 * <p>Sibling of {@code LivingEntityHitboxAccessor}, and there for the same kind of
 * reason: the information is there, the accessor vanilla offers is unusable here.
 */
@Mixin(Entity.class)
public interface EntityIdAccessor {
	@Accessor("id")
	int grandcraft$rawId();
}
