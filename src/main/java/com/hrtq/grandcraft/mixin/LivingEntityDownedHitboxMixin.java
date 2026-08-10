package com.hrtq.grandcraft.mixin;

import com.hrtq.grandcraft.combat.Downed;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives a downed actor a hurtbox the shape of the body actually on the ground.
 *
 * <p>Reported in game 2026-08-09: <em>"mobs can hardly hit the player when down"</em>.
 * The state shipped deliberately keeping the standing box — noted at the time as
 * "cheap to revisit" — and that turned out to be the wrong call in a way the reasoning
 * missed. A standing box is a 1.8 block column, and a mob's melee test is its own
 * attack box <em>intersected with the target's hitbox</em>
 * ({@code MobMeleeRangeMixin} documents the shape). The column is not the problem on
 * its own; the problem is that it is a column standing where the drawn body is lying
 * down, so the thing being hit and the thing being aimed at are a metre apart and
 * every attack reads as a miss whether or not it landed.
 *
 * <p><strong>Height only — the footprint is left alone.</strong> Widening the box to
 * match a body's real two-block length is the obvious idea and a bad one: width is
 * what decides whether an actor fits through a gap and whether it is shoved out of
 * blocks, so a downed player in a doorway would be extruded into the wall. A short box
 * at the player's own position is enough for a mob standing over them to connect,
 * which is the whole complaint.
 *
 * <p>The eye height falls out of {@link EntityDimensions#scalable} at 85% of the
 * height, which drops the first-person camera to about knee level. That is a bonus
 * rather than the point, and it is the reason no separate eye-height override is
 * needed.
 *
 * <p><strong>Both sides, and that is load-bearing.</strong> {@code Downed.isDowned}
 * answers from the controller on the server and from the phase view on a client, so
 * the two agree about the box. They must: the client predicts its own collisions
 * against whatever this returns, and a disagreement is a player who rubber-bands.
 */
@Mixin(LivingEntity.class)
public class LivingEntityDownedHitboxMixin {
	/**
	 * How tall a prone actor is, in blocks.
	 *
	 * <p>Vanilla's own crawling figure. Nothing is gained by inventing a number when
	 * the game already has one for a body on the floor, and matching it means a downed
	 * player fits wherever a crawling one does.
	 */
	private static final float PRONE_HEIGHT = 0.6F;

	@Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
	private void grandcraft$proneHitbox(Pose pose, CallbackInfoReturnable<EntityDimensions> info) {
		LivingEntity self = (LivingEntity) (Object) this;

		// Players first, and it is not only an optimisation. Only a player can hold the
		// DOWNED verb, so every other actor is a wasted question — and this method is
		// asked constantly, including from constructors, where the less this touches the
		// better. The salmon that killed the connection would never have got past this
		// line.
		if (!(self instanceof Player) || !Downed.isDowned(self)) {
			return;
		}

		EntityDimensions standing = info.getReturnValue();

		// Never taller than the actor already is: this shortens a player, and must not
		// silently grow anything that was already lower than the figure above.
		if (standing == null || standing.height() <= PRONE_HEIGHT) {
			return;
		}

		info.setReturnValue(EntityDimensions.scalable(standing.width(), PRONE_HEIGHT));
	}
}
