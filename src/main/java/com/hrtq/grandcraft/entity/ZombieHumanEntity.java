package com.hrtq.grandcraft.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.util.GeckoLibUtil;
import com.hrtq.grandcraft.combat.CombatPhaseAnimations;
import com.hrtq.grandcraft.combat.CombatState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A zombie-shaped mob drawn and animated by GeckoLib rather than by the vanilla rig.
 *
 * <h2>Why this is not a subclass of Zombie</h2>
 * Extending {@code Zombie} would hand it working AI for free, but
 * {@code CombatActor.forEntity} matches on entity class, so it would silently be
 * treated as a ZOMBIE and inherit that actor's tuning. It would also drag in
 * drowned conversion, baby variants and door breaking, none of which belong to a
 * test mob. A plain {@code Monster} with the same handful of goals is clearer and
 * leaves it free to have its own combat entry.
 *
 * <h2>Animation</h2>
 * The Bedrock files are authored in Blockbench and read directly by GeckoLib, which
 * is the point of the whole exercise. It does mean this mob sits outside
 * {@code HumanoidCombatPose}, which animates by mixing into vanilla's
 * {@code HumanoidModel} — there is no such model here to mix into. Combat phases
 * reach it through {@link CombatPhaseAnimations} instead, which is what lets it hold
 * {@code CombatVerb.PHASED_MELEE} honestly: an attack wind-up nobody can see is
 * indistinguishable from a broken one.
 *
 * <p>The clips come from two rounds of authoring and are named inconsistently: the
 * first three carry an {@code animation.zombie_human.} prefix, the later ones are
 * bare. GeckoLib does not care, but the strings here must match the JSON keys
 * exactly.
 */
public class ZombieHumanEntity extends Monster implements GeoEntity {
	/** Names are the animation keys as they appear in the .animation.json. */
	private static final RawAnimation WALK =
			RawAnimation.begin().thenLoop("animation.zombie_human.new_walking");
	private static final RawAnimation SWIM =
			RawAnimation.begin().thenLoop("animation.zombie_human.swimming");
	private static final RawAnimation ATTACK =
			RawAnimation.begin().thenPlayAndHold("animation.zombie_human.attack_bare_hand");

	/**
	 * The clips added later, which use bare names rather than the
	 * {@code animation.zombie_human.} prefix the first three carry. Both forms work —
	 * GeckoLib looks up whatever the JSON key is — but the spelling has to match
	 * exactly, {@code Iddle} included.
	 */
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("Idle");
	private static final RawAnimation STAGGER = RawAnimation.begin().thenPlayAndHold("stagger");
	private static final RawAnimation WIND_UP = RawAnimation.begin().thenPlayAndHold("wind_up");

	/**
	 * Which clip plays in which combat phase.
	 *
	 * <p>Each is stretched to fill its phase, so the numbers in
	 * {@code /grandcraft config combat} drive playback speed: a longer wind-up plays
	 * the same clip more slowly, and a heavier stagger holds its flinch for longer.
	 *
	 * <p>{@code wind_up} raises both arms 40 degrees on X — the forward swing axis, so
	 * it changes the silhouette and reads at distance — while the head rears back and
	 * then snaps forward into the blow. Authored at 19 ticks, so a startup near that
	 * plays it as drawn; further away and the scaling stretches or compresses it.
	 *
	 * <p>{@code attack_bare_hand} stands in as the slam until a dedicated one exists.
	 *
	 * <p>Still missing: a settle for {@code ATTACK_RECOVERY}, which stays unmapped and
	 * leaves locomotion running.
	 */
	private static final CombatPhaseAnimations COMBAT = new CombatPhaseAnimations()
			.on(CombatState.ATTACK_STARTUP, WIND_UP)
			.on(CombatState.ATTACK_ACTIVE, ATTACK)
			.on(CombatState.STAGGERED, STAGGER);

	/** Ticks blended between one animation and the next. */
	private static final int TRANSITION_TICKS = 5;

	/**
	 * Just enough blend to take the edge off entering a phase and handing back to
	 * locomotion, without eating the phase itself. Zero snapped visibly; the whole
	 * damage window is only two ticks, so this cannot go much higher either.
	 */
	private static final int COMBAT_TRANSITION_TICKS = 2;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	public ZombieHumanEntity(EntityType<? extends ZombieHumanEntity> type, Level level) {
		super(type, level);
	}

	/**
	 * Vanilla-zombie-ish baselines. Health, damage and speed are all multiplied by
	 * whatever this individual rolls, so these are the middle of the range rather
	 * than the final numbers.
	 */
	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.23)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.FOLLOW_RANGE, 35.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		// MeleeAttackGoal specifically: it is the goal GrandCraft's melee phasing
		// hooks, so using it is what lets this mob join the combat system.
		//
		// The true is load-bearing and is NOT vanilla's choice. With false,
		// canContinueToUse is `!navigation.isDone()` — the goal lives only while the
		// mob is still walking somewhere. Vanilla can afford that because its attack
		// resolves in the single tick it arrives, but a phased attack needs the goal
		// running for the whole wind-up, window and endlag. With false the goal died
		// on arrival and could only restart through canUse's 20 tick throttle, so the
		// mob attacked roughly once every ten seconds and read as doing no damage.
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		// GeckoLib 5 controllers take no animatable argument — (name, transition, handler).
		controllers.add(new AnimationController<ZombieHumanEntity>(
				"movement", TRANSITION_TICKS, this::movement));

		// Declared after movement so that when a phase is running its clip wins on any
		// bone both of them touch — the arms, mostly.
		controllers.add(new AnimationController<ZombieHumanEntity>(
				"combat", COMBAT_TRANSITION_TICKS, this::combat));
	}

	private PlayState movement(AnimationTest<ZombieHumanEntity> test) {
		// Read off this instance rather than test.animatable(): the handler is bound to
		// the entity being animated, so they are the same object, and this avoids
		// depending on how the record's type parameter is declared.
		if (isInWater()) {
			return test.setAndContinue(SWIM);
		}

		if (test.isMoving()) {
			return test.setAndContinue(WALK);
		}

		return test.setAndContinue(IDLE);
	}

	/**
	 * Held on its own controller so a swing plays over the legs still walking.
	 *
	 * <p>Driven by the combat phase rather than by a trigger on impact, which is the
	 * whole point: a trigger can only fire once damage has landed, and by then there is
	 * nothing left to react to. Reading the phase means the wind-up is on screen while
	 * it is still a wind-up.
	 */
	private PlayState combat(AnimationTest<ZombieHumanEntity> test) {
		return COMBAT.play(test, getId());
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
