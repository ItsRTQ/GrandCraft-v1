package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.render.CombatPoseState;
import com.hrtq.grandcraft.combat.CombatState;
import com.hrtq.grandcraft.combat.WeaponCategory;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Gives every living entity's render state somewhere to carry its combat phase.
 *
 * <p>Pure storage. It is filled by {@code LivingEntityRendererExtractMixin} and
 * read by the model and rotation mixins; nothing here decides anything.
 *
 * <p>Defaults to NEUTRAL so a render state that is never filled — every entity
 * that is not a GrandCraft combatant — reads as "not fighting" rather than as
 * uninitialised.
 */
@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements CombatPoseState {
	@Unique
	private CombatState grandcraft$phase;

	@Unique
	private float grandcraft$phaseProgress;

	@Unique
	private Float grandcraft$travelYaw;

	@Unique
	private WeaponCategory grandcraft$weapon;

	@Override
	public CombatState grandcraft$phase() {
		// Left unset rather than initialised: a mixin field initializer has to be
		// merged into the target's constructors, and reading null as "not fighting"
		// gets the same answer without depending on that.
		return this.grandcraft$phase == null ? CombatState.NEUTRAL : this.grandcraft$phase;
	}

	@Override
	public float grandcraft$phaseProgress() {
		return this.grandcraft$phaseProgress;
	}

	@Override
	public Float grandcraft$travelYaw() {
		return this.grandcraft$travelYaw;
	}

	@Override
	public WeaponCategory grandcraft$weapon() {
		return this.grandcraft$weapon;
	}

	@Override
	public void grandcraft$setPhase(CombatState phase, float progress, Float travelYaw,
			WeaponCategory weapon) {
		this.grandcraft$phase = phase;
		this.grandcraft$phaseProgress = progress;
		this.grandcraft$travelYaw = travelYaw;
		this.grandcraft$weapon = weapon;
	}
}
