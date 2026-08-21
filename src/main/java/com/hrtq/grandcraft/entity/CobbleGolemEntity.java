package com.hrtq.grandcraft.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.util.GeckoLibUtil;
import com.hrtq.grandcraft.combat.CombatController;
import com.hrtq.grandcraft.combat.CombatPhaseAnimations;
import com.hrtq.grandcraft.combat.CombatProfile;
import com.hrtq.grandcraft.combat.CombatProfiles;
import com.hrtq.grandcraft.combat.CombatState;
import com.hrtq.grandcraft.combat.GrandCraftCombat;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * The cobble golem: a slow, heavy thing that lies dormant until someone walks
 * too close, and lies back down once it has lost them.
 *
 * <p>Read {@link ZombieHumanEntity} first for why a custom mob extends
 * {@code Monster} rather than a vanilla type, why it is drawn by GeckoLib, and why
 * {@code MeleeAttackGoal}'s third argument is {@code true}. All three apply here
 * unchanged. What is new is everything below.
 *
 * <h2>Dormancy is a third stance, not a mode of the combat state machine</h2>
 * {@link Stance} sits alongside {@code CombatState} rather than inside it, because
 * the two answer different questions: {@code CombatState} is what this actor is
 * doing in a fight, and a dormant golem is not in one. Keeping them apart is what
 * lets the combat controller stay entirely unaware of this mob — a dormant golem
 * simply never starts an attack, because it has no target to attack.
 *
 * <p>The stance is <strong>synced</strong> ({@link #DATA_STANCE}) because the
 * animation controller runs on the client and has to pick between the settled hold,
 * the rise, and ordinary locomotion. It is also <strong>saved</strong>: a golem
 * placed dormant in a room must still be dormant after a reload, or every dormant
 * mob in a world stands up the first time the chunk loads.
 *
 * <h2>How it is held still: control flags, not NoAI</h2>
 * {@code Mob.serverAiStep} is {@code final} in 26.2, so a dormant mob cannot be
 * held still by skipping it, and {@code setNoAi} is far too blunt — it would take
 * physics with it. Instead the goals are switched off at the source with
 * {@code GoalSelector.setControlFlag}, which is vanilla's own mechanism for
 * exactly this and which also <em>stops</em> goals that are already running rather
 * than only preventing new ones.
 *
 * <p>The flags are re-asserted every tick from {@link #customServerAiStep} rather
 * than only when the stance changes. That costs two {@code EnumSet} writes and
 * makes the stance the single source of truth: nothing can leave the goals and the
 * stance disagreeing, which is the failure that would read as "it woke up and just
 * stood there". Nothing else in the mod touches control flags, so there is nobody
 * to fight over them.
 *
 * <p>{@code JUMP} is deliberately never disabled — it is the only flag
 * {@code FloatGoal} holds, and a dormant golem that cannot swim would drown in the
 * one situation it cannot react to.
 *
 * <h2>The clips, and what the delivery actually contains</h2>
 * The animator's names do not describe the poses, and the mapping below follows the
 * poses. Measured off the delivery:
 * <ul>
 *   <li>{@code settle} ends on the <em>model rest pose</em> and holds it — upright,
 *       arms down, entirely motionless. That is the dormant statue, and it is the
 *       only pose in the whole set with no motion in it at all.</li>
 *   <li>{@code settle}'s <em>first</em> frame is, bone for bone, the last frame of
 *       {@code hit}. As authored it is the attack's return to rest, which is why it
 *       is mapped to {@code ATTACK_RECOVERY} as well. The two uses never overlap —
 *       a dormant golem is not mid-swing — and they are on different controllers,
 *       so each keeps its own timeline.</li>
 *   <li>{@code idle} is not an upright idle: it is a low hunched crouch, breathing
 *       over five seconds. {@code alert_player} starts on that exact pose (within
 *       one model unit) and rises out of it. So waking blends <em>down</em> into the
 *       crouch and then rears up, which is the read this mob wants anyway.</li>
 * </ul>
 */
public class CobbleGolemEntity extends Monster implements GeoEntity {
	/**
	 * What the golem is doing outside of combat. Synced as a byte by ordinal, so
	 * <strong>do not reorder</strong> — a saved world stores the ordinal too.
	 */
	public enum Stance {
		/** Settled and holding still. Goals off, no target, minimal detection. */
		DORMANT,
		/** Rising. Goals still off so the rise is never walked through. */
		WAKING,
		/** Ordinary hostile behaviour. */
		AWAKE;

		private static final Stance[] BY_ORDINAL = values();

		static Stance byOrdinal(byte ordinal) {
			return ordinal >= 0 && ordinal < BY_ORDINAL.length ? BY_ORDINAL[ordinal] : DORMANT;
		}
	}

	private static final EntityDataAccessor<Byte> DATA_STANCE =
			SynchedEntityData.defineId(CobbleGolemEntity.class, EntityDataSerializers.BYTE);

	/**
	 * How close a player has to be for a dormant golem to notice them, in blocks —
	 * deliberately far short of {@code FOLLOW_RANGE}, which is what it uses once
	 * awake. Eight is about a corridor's length: close enough that walking into the
	 * room wakes it, far enough that it is not already swinging when it does.
	 *
	 * <p>This is the one number that decides whether the dormant state is an ambush
	 * or just flavour, so it is here rather than folded into a goal's constructor.
	 */
	private static final double DORMANT_DETECT_RANGE = 8.0;

	/**
	 * How long {@link Stance#WAKING} lasts.
	 *
	 * <p>Derived from the clip, not chosen: {@code alert_player} is 1.1667s, which is
	 * 23.3 ticks, and the movement controller spends {@link #TRANSITION_TICKS}
	 * blending into it first. Twenty-eight covers both with a few ticks of the held
	 * last frame to spare — {@code thenPlayAndHold} freezes rather than restarting, so
	 * overshooting is safe and undershooting would cut the rise off halfway.
	 */
	private static final int WAKE_TICKS = 28;

	/**
	 * How long an awake golem keeps looking before settling again — twenty seconds,
	 * the user's figure. Reset to full every tick it can still see a target, so this
	 * is time <em>since losing</em> the player rather than time since waking.
	 */
	private static final int SEARCH_TICKS = 400;

	/**
	 * The slam's reach, in blocks, measured from the golem's centre — the user's
	 * figure (2026-08-20).
	 *
	 * <p><strong>One number drives both the hitbox and the telegraph</strong>, and that
	 * is the whole point of it being a single constant: the ring of particles is drawn
	 * at this radius and everything inside it is hit, so what the player is shown and
	 * what actually lands cannot drift apart. That is the same rule
	 * {@code CombatPhaseAnimations} exists to enforce for clip timing. Changing this
	 * moves both together; nothing else needs touching.
	 */
	private static final double ATTACK_RADIUS = 6.5;

	/** Keeps the ring clear of the block it stands on rather than buried in it. */
	private static final double TELEGRAPH_LIFT = 0.1;

	/**
	 * Where in the {@code hit} clip the fists actually reach the ground.
	 *
	 * <p><strong>Measured off the shipped clip, not guessed.</strong> The fists are
	 * still 23 model units up at t=0.1667 and are on the floor by t=0.25, where they
	 * stay for the rest of it — so the blow lands 30% of the way in, and the whole
	 * back two thirds of the clip is follow-through.
	 *
	 * <p>This is the fix for the thing that read as the effects being out of sync:
	 * {@code CombatPhaseAnimations} stretches the clip across the whole active window,
	 * so firing on the window's <em>start</em> put the boom and the finished ring about
	 * three ticks — 150ms — ahead of the arms. Both now wait for this fraction of the
	 * window instead. <strong>The moment the fists land is a property of the clip</strong>,
	 * so a re-delivery that moves the strike moves this number with it.
	 */
	private static final double HIT_CLIP_LENGTH = 0.8333;
	private static final double HIT_CLIP_IMPACT = 0.25;
	private static final double IMPACT_FRACTION = HIT_CLIP_IMPACT / HIT_CLIP_LENGTH;

	/**
	 * How each mote is thrown, in blocks per tick — outward along the ground, and
	 * <em>downward</em>.
	 *
	 * <p>The downward part is not a stylistic choice, it is a correction. Both particles
	 * tried here carry a <strong>negative gravity</strong> of their own, so launched
	 * with no velocity at all they float upward and a fresh ring every tick becomes a
	 * rising column rather than a shockwave.
	 *
	 * <p><strong>Both numbers were recalibrated for {@code POOF}</strong>
	 * ({@code ExplodeParticle}: {@code gravity -0.1}, {@code friction 0.9}), which is a
	 * different animal from the {@code trial_spawner_detection} they were first tuned
	 * against ({@code friction 0.96}). The heavier friction kills a launch velocity two
	 * and a half times faster, so both had to grow to have the same effect: the
	 * downward push went -0.053 → -0.08 and the outward throw 0.02 → 0.05, which puts
	 * a typical puff's outward drift back around half a block.
	 *
	 * <p><strong>The rise cannot be flattened outright, and that is a property of the
	 * particle.</strong> A launch velocity decays but the negative gravity does not, so
	 * however hard a puff is thrown down it eventually climbs — and {@code POOF} lives
	 * anywhere from 18 to 82 ticks. At -0.08 a typical one stays under a fifth of a
	 * block while the longest-lived still drift about two. Pushing harder only pins
	 * them to the floor for longer, it does not change where the old ones end up.
	 * <strong>One number to nudge</strong>: less negative rises sooner.
	 */
	private static final double TELEGRAPH_OUTWARD = 0.05;
	private static final double TELEGRAPH_RISE = -0.08;

	/**
	 * Particles per block of circumference, so the ring stays evenly dense as it grows
	 * instead of thinning out, with a floor and a ceiling on the count.
	 *
	 * <p>The ceiling matters: a ring is drawn one particle per packet — {@code count}
	 * above zero scatters randomly inside a box and cannot draw a circle — so this is
	 * also the per-tick packet budget. At the full radius it works out at 24.
	 */
	private static final double TELEGRAPH_DENSITY = 1.0;
	private static final int TELEGRAPH_MIN_POINTS = 8;
	private static final int TELEGRAPH_MAX_POINTS = 64;

	/**
	 * The slam's impact sound: a TNT blast and a stone block breaking, both dropped an
	 * octave and to half speed.
	 *
	 * <p><strong>Pitch and speed are the same control</strong> — Minecraft has no time
	 * stretch, so a sound played lower is played slower by exactly the same factor, and
	 * asking for both is asking for one number. <strong>0.5 is the floor</strong>:
	 * {@code SoundEngine} clamps pitch to {@code [0.5, 2.0]} (verified in the 26.2
	 * client jar), so anything lower in the packet is silently pulled back up to this.
	 * Going deeper than an octave would mean shipping a pre-processed {@code .ogg} and
	 * a {@code sounds.json}, which is a real option but a different job.
	 *
	 * <p><strong>Above 1.0, volume buys range rather than loudness.</strong> OpenAL
	 * clamps gain at 1.0, and vanilla sends a sound to players within
	 * {@code volume > 1.0 ? volume * 16 : 16} blocks — so the blast's original 1.6 was
	 * not a loud sound, it was a normal one audible for 25 blocks, and trimming it to
	 * 1.0 would have changed nothing a nearby player could hear. Quieting it therefore
	 * means going <em>below</em> one, which 0.7 does; the cost is that it now carries
	 * 16 blocks like everything else.
	 *
	 * <p>That also flips the balance on purpose: the rubble is now the louder of the
	 * two, so the crack of stone leads and the blast is the body underneath it rather
	 * than the other way round.
	 */
	private static final float IMPACT_PITCH = 0.5F;
	private static final float IMPACT_BLAST_VOLUME = 0.7F;
	private static final float IMPACT_RUBBLE_VOLUME = 1.2F;

	/** Names are the animation keys as they appear in the .animation.json. */
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");

	/**
	 * The two stance clips. Both hold rather than loop: {@code settle} freezes on the
	 * motionless rest pose, which is the whole point of it, and {@code alert_player}
	 * freezes on standing so a wake that outlasts the clip does not replay the rise.
	 */
	private static final RawAnimation SETTLE = RawAnimation.begin().thenPlayAndHold("settle");
	private static final RawAnimation ALERT = RawAnimation.begin().thenPlayAndHold("alert_player");

	/** The attack, delivered already split — unlike every previous mob's. */
	private static final RawAnimation WIND_UP = RawAnimation.begin().thenPlayAndHold("wind_up");
	private static final RawAnimation STRIKE = RawAnimation.begin().thenPlayAndHold("hit");
	private static final RawAnimation RECOVER = RawAnimation.begin().thenPlayAndHold("settle");
	private static final RawAnimation STAGGER = RawAnimation.begin().thenPlayAndHold("stagger");

	/**
	 * Which clip plays in which combat phase. Each is stretched to fill its phase, so
	 * the numbers on {@code /grandcraft config combat} drive playback speed.
	 *
	 * <p>This is the first delivery to arrive with a separate wind-up, so nothing had
	 * to be split. It did need a <strong>hold</strong>: as delivered {@code wind_up}
	 * was 10.8 clip ticks of continuous raise and nothing else, and stretched over a
	 * startup several times that length it becomes a slow-motion arm lift rather than
	 * a telegraph. The shipped copy repeats its last keyframe out to <strong>1.2s —
	 * 24 ticks, the startup exactly</strong> — so the raise still plays over the ten
	 * ticks it was drawn for and the charged pose is held for the fourteen after it.
	 * Both keyframes are linear, because a catmull-rom keyframe takes its tangent from
	 * its neighbours and two equal endpoints bulge out and come back instead of holding.
	 *
	 * <p><strong>That extension is not optional, it is the price of a slow attack.</strong>
	 * {@code CombatPhaseAnimations} stretches whatever it is given across the whole
	 * phase, so lengthening the startup without lengthening the hold to match would
	 * spread the ten tick raise over twenty-four and undo the fix. If the startup on
	 * {@code /grandcraft config combat} is moved again, the hold keyframe moves with
	 * it: {@code hold seconds = 0.5 * startupTicks / 10}.
	 *
	 * <p>{@code ATTACK_RECOVERY} is mapped, which no previous mob managed: the
	 * delivery contains the return-to-rest that the other two lacked. It is the same
	 * clip the dormant hold uses, for the reason in the class javadoc.
	 *
	 * <p>{@code GUARDING} must never be mapped here — a held guard arrives as short
	 * repeating leases, so scaling to it sawtooths. See {@link CombatPhaseAnimations}.
	 */
	private static final CombatPhaseAnimations COMBAT = new CombatPhaseAnimations()
			.on(CombatState.ATTACK_STARTUP, WIND_UP)
			.on(CombatState.ATTACK_ACTIVE, STRIKE)
			.on(CombatState.ATTACK_RECOVERY, RECOVER)
			.on(CombatState.STAGGERED, STAGGER);

	/** Ticks blended between one animation and the next. */
	private static final int TRANSITION_TICKS = 5;

	/** Short, or the blend eats the phase it is meant to be showing. */
	private static final int COMBAT_TRANSITION_TICKS = 2;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	/** Server-side only; the client is told the stance, never the countdowns. */
	private int wakingTicks;
	private int searchTicks = SEARCH_TICKS;

	/**
	 * The slam's own clock, counted here rather than read off the combat phases.
	 *
	 * <p>It has to be its own count because the effects <strong>span two phases</strong>:
	 * the ring grows through the whole wind-up and on into the first third of the
	 * strike, and the fists land partway through the active window rather than at
	 * either boundary. Stitching that out of two separate phase countdowns each tick
	 * was the confusing version; one counter that starts when the wind-up does and
	 * knows which of its own ticks is the impact is the clear one.
	 *
	 * <p>{@code -1} means no slam is running. Reset the moment the phase is neither
	 * startup nor active, which is also what makes a stagger mid-wind-up cancel the
	 * telegraph rather than leave it hanging. Deliberately not saved.
	 */
	private int slamTicks = -1;
	private int slamTelegraphFrom;
	private int slamImpactTick;
	private boolean slamLanded;

	public CobbleGolemEntity(EntityType<? extends CobbleGolemEntity> type, Level level) {
		super(type, level);

		// Mob's constructor has already run registerGoals, so the selectors exist and
		// the very first tick is already gated correctly rather than getting one free
		// tick of strolling before customServerAiStep catches up.
		applyStanceToGoals();
	}

	/**
	 * A slow, heavy tank — the mod's first genuinely slow opponent.
	 *
	 * <p>Two and a half times a zombie's health at four fifths of its speed, hitting
	 * for four times as much — and the damage is what pays for the 2.9 second cycle and
	 * the six and a half block circle it lands in. A slam that a player can see coming
	 * for over a second, and can outrun, is allowed to hurt. Every one of these is multiplied by whatever this
	 * individual rolls, so they are the middle of the range rather than the final
	 * numbers, and the phase timings on the combat tab are what really pace it.
	 *
	 * <p>Knockback resistance is the one attribute neither other custom mob sets, and
	 * it is here because a two and a half block pile of cobblestone that skids
	 * backwards off every hit reads as light. Six tenths still lets a solid blow move
	 * it, which is worth keeping — total immunity would remove the player's only way
	 * to make space.
	 */
	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 50.0)
				.add(Attributes.MOVEMENT_SPEED, 0.18)
				.add(Attributes.ATTACK_DAMAGE, 12.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
				.add(Attributes.FOLLOW_RANGE, 35.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STANCE, (byte) Stance.DORMANT.ordinal());
	}

	/**
	 * The same goals every custom mob has. None of them know about dormancy —
	 * {@link #applyStanceToGoals} switches them off wholesale instead, so a goal added
	 * here later is gated for free.
	 */
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

	/**
	 * The stance machine. Runs after the goal selectors have ticked, which is why the
	 * flags are asserted here for the <em>next</em> tick rather than acted on
	 * immediately — one tick of lag on a state that lasts seconds.
	 */
	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		switch (stance()) {
			case DORMANT -> {
				// Deliberately a plain proximity test rather than a targeting goal:
				// a goal would use FOLLOW_RANGE and see across the room, and the
				// short range is the entire mechanic. getNearestPlayer skips
				// creative and spectator players for us.
				if (level.getNearestPlayer(this, DORMANT_DETECT_RANGE) != null) {
					wake();
				}
			}
			case WAKING -> {
				if (--this.wakingTicks <= 0) {
					setStance(Stance.AWAKE);
				}
			}
			case AWAKE -> {
				if (getTarget() != null) {
					this.searchTicks = SEARCH_TICKS;
				} else if (--this.searchTicks <= 0) {
					settle();
				}
			}
		}

		applyStanceToGoals();
		tickSlam(level);
	}

	/**
	 * The slam's two pieces of feedback, both driven off the combat phase.
	 *
	 * <p>The controller is read once here and handed down, rather than looked up in
	 * each of them: they are two halves of one attack and must never end up describing
	 * different ticks of it.
	 */
	private void tickSlam(ServerLevel level) {
		CombatController controller = GrandCraftCombat.controllerOf(this);
		CombatState phase = controller.state();

		if (phase != CombatState.ATTACK_STARTUP && phase != CombatState.ATTACK_ACTIVE) {
			this.slamTicks = -1;
			return;
		}

		if (this.slamTicks < 0) {
			beginSlam(controller, phase);
		} else {
			this.slamTicks++;
		}

		if (!this.slamLanded && this.slamTicks >= this.slamImpactTick) {
			playSlamImpact(level);
			this.slamLanded = true;
		}

		if (this.slamTicks >= this.slamTelegraphFrom && this.slamTicks <= this.slamImpactTick) {
			drawSlamTelegraph(level);
		}
	}

	/**
	 * Works out, once, which tick of this slam the fists land on.
	 *
	 * <p>Doing it up front is what lets the ring be a single ramp across two phases
	 * instead of two ramps stitched at a boundary. The active window is read from the
	 * profile rather than waited for, because while the wind-up is running the
	 * controller is reporting the <em>wind-up's</em> length and the number needed is
	 * the next phase's.
	 *
	 * <p>Normally entered on the first tick of the wind-up. Entering on the active
	 * window instead is legal and handled — a startup configured to zero would do it —
	 * and simply compresses the whole ring into the run-up to the blow.
	 */
	private void beginSlam(CombatController controller, CombatState phase) {
		CombatProfile profile = CombatProfiles.forEntity(this);
		int startup = phase == CombatState.ATTACK_STARTUP ? controller.phaseTotalTicks() : 0;
		int active = profile != null ? profile.melee().activeTicks() : controller.phaseTotalTicks();

		this.slamTicks = 0;
		this.slamTelegraphFrom = startup;
		this.slamImpactTick = Math.max(startup + (int) Math.round(active * IMPACT_FRACTION), 1);
		this.slamLanded = false;
	}

	/**
	 * The fists landing: a TNT blast and a stone block breaking, together, an octave
	 * down and at half speed.
	 *
	 * <p>Fired on the tick the <strong>active window opens</strong>, which is the frame
	 * the arms come down — not when a target is hit. So a slam that everybody dodged
	 * still lands on the ground and still booms, which is both what "hits the ground"
	 * means and the more useful sound: the player who got out of the ring hears exactly
	 * what they escaped. It is also why this is not in {@link #doHurtTarget}, which
	 * only runs when the swing connects.
	 *
	 * <p>Placed at {@link #getY()} — the feet, where the blow actually lands and where
	 * the telegraph ring was drawn — rather than at the mob's centre.
	 *
	 * <p>{@code getSoundSource()} rather than a hardcoded category, so it sits under the
	 * player's Hostile Creatures slider along with everything else this mob may make.
	 */
	private void playSlamImpact(ServerLevel level) {
		// A null first argument excludes nobody, so every player in range hears it.
		level.playSound(null, getX(), getY(), getZ(),
				SoundEvents.GENERIC_EXPLODE, getSoundSource(),
				IMPACT_BLAST_VOLUME, IMPACT_PITCH);
		level.playSound(null, getX(), getY(), getZ(),
				SoundEvents.STONE_BREAK, getSoundSource(),
				IMPACT_RUBBLE_VOLUME, IMPACT_PITCH);
	}

	/**
	 * The expanding ring that tells the player how far the slam reaches.
	 *
	 * <p><strong>It starts when the wind-up ends and finishes as the fists land</strong>
	 * (user, 2026-08-20) — not across the wind-up, which is where it began. So it is a
	 * shockwave thrown out by the blow rather than a warning drawn before it. The fists
	 * land 30% into the strike, so a 14 tick window gives it five rings, evenly spaced
	 * out to the full {@link #ATTACK_RADIUS}, the last landing on the same tick as the
	 * damage and the sound. <strong>The number of rings is the hit window's to set</strong>
	 * — a shorter one packs them closer together and reads as a coarser wave.
	 *
	 * <p><strong>A puff's own life cannot be shortened from here.</strong>
	 * {@code POOF} is {@code ExplodeParticle}, which sets
	 * {@code lifetime = (int)(16.0 / (nextFloat() * 0.8 + 0.2)) + 2} — 18 to 82 ticks,
	 * averaging about 34 — and none of it depends on anything the spawn call passes.
	 * That is <em>longer</em> than the {@code trial_spawner_detection} it replaced
	 * (12 to 24), so the tail after a slam grew when the particle changed. Only the
	 * spawn window is controllable from this end, and it is already as tight as the
	 * design allows: five rings across the strike rather than seventeen across the
	 * whole wind-up.
	 *
	 * <p>{@code POOF} is also a much larger sprite, which is why
	 * {@link #TELEGRAPH_DENSITY} dropped to one puff per block of circumference — at
	 * the old 1.5 the outer ring alone was sixty overlapping puffs.
	 *
	 * <p>{@code overrideLimiter} is true, so a player running reduced particles still
	 * sees the extent of what just hit them.
	 */
	private void drawSlamTelegraph(ServerLevel level) {
		int steps = this.slamImpactTick - this.slamTelegraphFrom + 1;
		int step = this.slamTicks - this.slamTelegraphFrom + 1;
		double radius = ATTACK_RADIUS * step / steps;

		if (radius <= 0.0) {
			return;
		}

		int points = (int) Math.round(2.0 * Math.PI * radius * TELEGRAPH_DENSITY);
		points = Math.max(TELEGRAPH_MIN_POINTS, Math.min(TELEGRAPH_MAX_POINTS, points));

		// The bottom centre, so the ring reads as travelling out along the ground from
		// under the golem rather than hanging around its waist.
		double y = getY() + TELEGRAPH_LIFT;

		for (int point = 0; point < points; point++) {
			double angle = 2.0 * Math.PI * point / points;
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);

			// A count of ZERO is what makes the three deltas a velocity rather than a
			// random position spread, and it still spawns exactly one mote at exactly
			// this point. That is the whole reason the motes can be aimed outward and
			// held down; a count of one would place them correctly and let the
			// particle's own negative gravity carry them up.
			level.sendParticles(ParticleTypes.POOF, true, false,
					getX() + cos * radius, y, getZ() + sin * radius,
					0,
					cos * TELEGRAPH_OUTWARD, TELEGRAPH_RISE, sin * TELEGRAPH_OUTWARD,
					1.0);
		}
	}

	/**
	 * The slam lands on <em>everything</em> in the ring, not just on the target that
	 * triggered it.
	 *
	 * <p>Vanilla's {@code checkAndPerformAttack} calls this once with the mob's own
	 * target, so overriding it is the whole of the change: the phase gating in
	 * {@code MeleeAttackGoalMixin} still decides <em>when</em> a blow may land, and
	 * each victim still goes through {@code Mob.doHurtTarget}, which is what keeps
	 * damage, knockback, difficulty scaling and — on a player — GrandCraft's own guard
	 * and dodge handling working exactly as they do for any other mob. Nothing about
	 * damage is reimplemented here; only <em>how many</em> targets it is applied to.
	 *
	 * <p>Other cobble golems are excluded. Without that, two of them standing together
	 * would slam each other, {@code HurtByTargetGoal} would have each retarget the
	 * other, and a group would fight itself instead of the player. Everything else
	 * living is fair game, which is what makes standing behind a mob no defence.
	 *
	 * <p><strong>Line of sight is checked for the target that commits the swing and
	 * not for the rest.</strong> That is deliberate — the blow is a shockwave along the
	 * ground, and the golem is not choosing the extra victims — but it does mean a
	 * slam reaches someone crouched behind a block within the radius.
	 */
	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean landed = false;

		for (LivingEntity victim : level.getEntitiesOfClass(
				LivingEntity.class, slamBox(), this::isSlamVictim)) {
			landed |= super.doHurtTarget(level, victim);
		}

		return landed;
	}

	/**
	 * Replaces vanilla's reach test with the slam's radius.
	 *
	 * <p><strong>This opts the golem out of {@code MobMeleeRangeMixin}</strong>, which
	 * gives every other actor a configurable reach by injecting into {@code Mob}'s copy
	 * of this method. An override is dispatched before the mixin is ever reached, and
	 * that is the intent — a golem's reach is a circle rather than an inflated box, and
	 * it is not the shared number. If reach ever stops behaving for this mob, that
	 * mixin is not where to look.
	 *
	 * <p>Getting this right matters as much as the hitbox does: {@code canPerformAttack}
	 * is what decides when the golem <em>commits</em>, and left on vanilla's roughly two
	 * block figure the golem would have walked to well inside its own ring before
	 * swinging, wasting most of the range the telegraph had just advertised.
	 */
	@Override
	public boolean isWithinMeleeAttackRange(LivingEntity target) {
		return withinSlam(target);
	}

	/** The golem's own box grown horizontally by the radius — the broad-phase query. */
	private AABB slamBox() {
		return getBoundingBox().inflate(ATTACK_RADIUS, 0.0, ATTACK_RADIUS);
	}

	private boolean isSlamVictim(LivingEntity victim) {
		return victim != this
				&& !(victim instanceof CobbleGolemEntity)
				&& victim.isAlive()
				&& withinSlam(victim);
	}

	/**
	 * Whether a target is inside the ring.
	 *
	 * <p>A true circle from the golem's centre to the <em>nearest point</em> of the
	 * target's box, rather than centre to centre, so a wide target is caught the moment
	 * any of it crosses the line the particles are drawn on. Using centres instead would
	 * make the two disagree by half a hitbox, which is the drift this whole design is
	 * arranged to avoid.
	 *
	 * <p>Vertically it is the golem's own height band and nothing more: the blow runs
	 * along the ground, so somebody standing on a pillar two blocks away is above it.
	 */
	private boolean withinSlam(LivingEntity target) {
		AABB box = target.getBoundingBox();
		AABB own = getBoundingBox();

		if (box.maxY < own.minY || box.minY > own.maxY) {
			return false;
		}

		double dx = Math.max(Math.max(box.minX - getX(), getX() - box.maxX), 0.0);
		double dz = Math.max(Math.max(box.minZ - getZ(), getZ() - box.maxZ), 0.0);

		return dx * dx + dz * dz <= ATTACK_RADIUS * ATTACK_RADIUS;
	}

	/**
	 * Waking on damage, so a dormant golem cannot be whittled down while it stands
	 * there taking it — which would read as broken rather than as stealthy.
	 *
	 * <p>Gated on the hit actually landing: a blow that was refused or absorbed
	 * outright never reached the golem, and waking on one would let an attack the
	 * player did not land give the game away.
	 */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);

		if (hurt && stance() == Stance.DORMANT) {
			wake();
		}

		return hurt;
	}

	/**
	 * Never despawns.
	 *
	 * <p>A golem left dormant in a room is scenery until someone walks into it, and
	 * scenery that evaporates when the player wanders 40 blocks away is a bug report
	 * rather than a feature. The other two custom mobs do not need this because they
	 * come at you immediately.
	 */
	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putByte("Stance", (byte) stance().ordinal());
		output.putInt("SearchTicks", this.searchTicks);
	}

	/**
	 * A golem that was placed dormant must still be dormant after a reload, or every
	 * one in the world stands up the first time its chunk loads.
	 *
	 * <p>A golem saved mid-rise comes back {@link Stance#AWAKE} rather than replaying
	 * the rise from wherever it got to: the countdown is not saved, and resuming an
	 * animation across a reload is not worth a field.
	 */
	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);

		Stance saved = Stance.byOrdinal(input.getByteOr("Stance", (byte) Stance.DORMANT.ordinal()));

		setStance(saved == Stance.WAKING ? Stance.AWAKE : saved);
		this.searchTicks = Math.max(input.getIntOr("SearchTicks", SEARCH_TICKS), 1);
		applyStanceToGoals();
	}

	public Stance stance() {
		return Stance.byOrdinal(getEntityData().get(DATA_STANCE));
	}

	private void setStance(Stance stance) {
		getEntityData().set(DATA_STANCE, (byte) stance.ordinal());
	}

	/** Starts the rise. The goals stay off until it finishes. */
	private void wake() {
		setStance(Stance.WAKING);
		this.wakingTicks = WAKE_TICKS;
		this.searchTicks = SEARCH_TICKS;
	}

	/** Back down. The clip plays itself: {@code settle} runs once and then holds. */
	private void settle() {
		setStance(Stance.DORMANT);
		setTarget(null);
		getNavigation().stop();
	}

	/**
	 * The one place the stance becomes behaviour.
	 *
	 * <p>{@code TARGET} and {@code LOOK} come back at the <em>start</em> of the rise
	 * rather than at the end of it, so the golem turns to face whoever woke it while
	 * it is still getting up, and is already looking at them when it can move.
	 * {@code MOVE} is the only flag that waits, because a golem that walks through its
	 * own rise never looks like it stood up.
	 *
	 * <p>The six goals' own flags are what make those three lines cover everything, and
	 * they were read out of the 26.2 jar rather than assumed: {@code FloatGoal} is
	 * {@code JUMP} alone, {@code MeleeAttackGoal} is {@code MOVE + LOOK},
	 * {@code WaterAvoidingRandomStrollGoal} is {@code MOVE}, {@code LookAtPlayerGoal} is
	 * {@code LOOK}, {@code RandomLookAroundGoal} is {@code MOVE + LOOK}, and both target
	 * goals are {@code TARGET}. A goal is blocked if <em>any</em> of its flags is
	 * disabled — which is why {@code MOVE} alone holds the rise still, and why during it
	 * {@code LookAtPlayerGoal} is the only thing left that can claim {@code LOOK}. The
	 * golem therefore turns to face the player rather than glancing idly around, and
	 * that falls out of the flags rather than out of goal priority.
	 *
	 * <p>{@code JUMP} is never touched — see the class javadoc.
	 */
	private void applyStanceToGoals() {
		Stance stance = stance();

		this.goalSelector.setControlFlag(Goal.Flag.MOVE, stance == Stance.AWAKE);
		this.goalSelector.setControlFlag(Goal.Flag.LOOK, stance != Stance.DORMANT);
		this.targetSelector.setControlFlag(Goal.Flag.TARGET, stance != Stance.DORMANT);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		// GeckoLib 5 controllers take no animatable argument — (name, transition, handler).
		controllers.add(new AnimationController<CobbleGolemEntity>(
				"movement", TRANSITION_TICKS, this::movement));

		// Declared after movement so that when a phase is running its clip wins on any
		// bone both of them touch — the arms, mostly.
		controllers.add(new AnimationController<CobbleGolemEntity>(
				"combat", COMBAT_TRANSITION_TICKS, this::combat));
	}

	/**
	 * Stance first, then locomotion.
	 *
	 * <p>Death is checked before everything because it is not a stance and not a
	 * combat phase — a corpse has stopped taking part — and a golem killed while
	 * dormant must fall over rather than keep holding its settled pose.
	 *
	 * <p>There is no swim clip in this delivery, so water falls through to whatever
	 * the stance says. {@code FloatGoal} still keeps it from drowning; only the
	 * paddling is missing.
	 */
	private PlayState movement(AnimationTest<CobbleGolemEntity> test) {
		if (isDeadOrDying()) {
			return test.setAndContinue(DEATH);
		}

		return switch (stance()) {
			case DORMANT -> test.setAndContinue(SETTLE);
			case WAKING -> test.setAndContinue(ALERT);
			case AWAKE -> test.setAndContinue(test.isMoving() ? WALK : IDLE);
		};
	}

	/** Held on its own controller so a swing plays over the legs still walking. */
	private PlayState combat(AnimationTest<CobbleGolemEntity> test) {
		return COMBAT.play(test, getId());
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
