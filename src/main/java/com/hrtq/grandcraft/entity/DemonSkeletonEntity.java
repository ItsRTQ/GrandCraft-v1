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
 * The demonic skeleton: the mod's second GeckoLib mob, and a straight copy of
 * {@link ZombieHumanEntity}'s shape.
 *
 * <p>Read that class first — its javadoc carries the reasons for everything repeated
 * here, in particular why a custom mob extends {@code Monster} rather than a vanilla
 * one ({@code CombatActor.forEntity} matches on entity class, so a {@code Skeleton}
 * subclass would be swallowed by that actor's entry, and would also drag in the bow AI
 * that never reaches the melee hook at all).
 *
 * <p>The delivered rig is bone-for-bone the same humanoid rig as the zombie-human's,
 * minus the {@code hat} bone, which is why the whole render path clones with nothing
 * new invented.
 *
 * <h2>The attack clip was split, and that is the only authored change</h2>
 * The delivery has no separate wind-up: {@code attack} welds the raise, the blow and
 * the return into half a second. {@link CombatPhaseAnimations} stretches one whole clip
 * over one phase, so the same clip on two phases would restart and re-scale at the
 * boundary and play the blow twice. {@code agent-memory/tools/split-geckolib-clip.py}
 * cut it in two at {@code 0.1667}, which is already an explicit keyframe on every
 * channel that moves — so it is a partition of the animator's numbers, not a resample —
 * and gave the wind-up half a hold so the raise occupies only its first quarter. Both
 * halves live in the shipped {@code .animation.json} beside the untouched original.
 */
public class DemonSkeletonEntity extends Monster implements GeoEntity {
	/**
	 * Names are the animation keys as they appear in the .animation.json, and the
	 * spelling has to match exactly — a name that does not resolve plays nothing at all,
	 * silently. {@code Walking} really is capitalised in the delivery and {@code idle}
	 * really is not.
	 */
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("Walking");
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");

	/** The two halves of the animator's {@code attack}, plus the delivered flinch. */
	private static final RawAnimation WIND_UP = RawAnimation.begin().thenPlayAndHold("wind_up");
	private static final RawAnimation STRIKE = RawAnimation.begin().thenPlayAndHold("strike");
	private static final RawAnimation STAGGER = RawAnimation.begin().thenPlayAndHold("stagger");

	/**
	 * Which clip plays in which combat phase. Each is stretched to fill its phase, so the
	 * numbers on {@code /grandcraft config combat} drive playback speed.
	 *
	 * <p>{@code wind_up} is 13.3 clip ticks against a 10 tick startup, and three quarters
	 * of it is the charged pose held — which is what a telegraph is. The raise alone would
	 * be 3.3 ticks stretched over 10, a slow-motion arm lift.
	 *
	 * <p>{@code strike} is 6.7 clip ticks against a 6 tick window, near enough its authored
	 * speed, and it ends on rest. That is why {@code ATTACK_RECOVERY} is left unmapped: the
	 * arms are already down when locomotion takes over, so there is nothing for a settle
	 * clip to do yet. Splitting a settle out instead would stretch 2.5 clip ticks over a 10
	 * tick recovery, which is four times slower than drawn.
	 *
	 * <p>{@code GUARDING} must never be mapped here — a held guard arrives as short
	 * repeating leases, so scaling to it sawtooths. See {@link CombatPhaseAnimations}.
	 */
	private static final CombatPhaseAnimations COMBAT = new CombatPhaseAnimations()
			.on(CombatState.ATTACK_STARTUP, WIND_UP)
			.on(CombatState.ATTACK_ACTIVE, STRIKE)
			.on(CombatState.STAGGERED, STAGGER);

	/** Ticks blended between one animation and the next. */
	private static final int TRANSITION_TICKS = 5;

	/**
	 * Short, because a blend is measured in ticks like everything else and a long one eats
	 * the phase it is meant to be showing. Two is the zombie-human's figure.
	 */
	private static final int COMBAT_TRANSITION_TICKS = 2;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	public DemonSkeletonEntity(EntityType<? extends DemonSkeletonEntity> type, Level level) {
		super(type, level);
	}

	/**
	 * Placeholders, deliberately: these are the zombie-human's numbers, and what is being
	 * proved in this slice is that the mob draws, animates and joins the combat system.
	 * Every one of them is multiplied by whatever this individual rolls, and the tuning
	 * that makes a demonic skeleton feel different from a zombie belongs to the behaviour
	 * slice that follows.
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
		// The true is load-bearing and is NOT vanilla's choice: with false the goal's
		// canContinueToUse is `!navigation.isDone()`, so it dies the moment the mob
		// arrives and a phased attack never gets to finish its wind-up. It could only
		// restart through canUse's 20 tick throttle, which reads as a mob that attacks
		// about once every ten seconds. tuning.md lesson 10.
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
		controllers.add(new AnimationController<DemonSkeletonEntity>(
				"movement", TRANSITION_TICKS, this::movement));

		// Declared after movement so that when a phase is running its clip wins on any
		// bone both of them touch — the arms, mostly.
		controllers.add(new AnimationController<DemonSkeletonEntity>(
				"combat", COMBAT_TRANSITION_TICKS, this::combat));
	}

	/**
	 * Locomotion, plus the death clip.
	 *
	 * <p>Death is here rather than on the combat controller because it is not a combat
	 * phase: {@code CombatState} has no entry for it, and a corpse has stopped taking
	 * part. Checked first so nothing else can win over it.
	 *
	 * <p>There is no swim clip in this delivery, so water falls through to {@link #IDLE}.
	 * The {@code FloatGoal} still keeps it from drowning; only the paddling is missing.
	 */
	private PlayState movement(AnimationTest<DemonSkeletonEntity> test) {
		if (isDeadOrDying()) {
			return test.setAndContinue(DEATH);
		}

		if (test.isMoving()) {
			return test.setAndContinue(WALK);
		}

		return test.setAndContinue(IDLE);
	}

	/** Held on its own controller so a swing plays over the legs still walking. */
	private PlayState combat(AnimationTest<DemonSkeletonEntity> test) {
		return COMBAT.play(test, getId());
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
