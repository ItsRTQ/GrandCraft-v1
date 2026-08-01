package com.hrtq.grandcraft.entity;

import com.hrtq.grandcraft.progression.EssenceAwards;
import com.hrtq.grandcraft.progression.LevelSettings;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A Life Essence Orb: the physical form of Essence Power, dropped by a mob a player
 * killed and collected by walking near it.
 *
 * <h2>Why this is not a subclass of {@code ExperienceOrb}</h2>
 * Extending vanilla's orb would have been shorter and is <strong>wrong</strong>.
 * {@code ExperienceOrb.scanForMerges} looks for {@code EntityTypeTest.forClass(
 * ExperienceOrb.class)} inside a small box and merges whatever it finds — and it is
 * <em>private</em>, so a subclass cannot opt out of it. A Life Essence Orb that
 * inherited from it would therefore merge with ordinary experience orbs and vice
 * versa, silently moving value between two systems that are meant to stay separate.
 * Vanilla experience is deliberately untouched by this feature, and this is the
 * single largest thing that could have broken that promise.
 *
 * <p>The movement, follow and pickup behaviour below is modelled on vanilla's orb so
 * these feel like something the game already had — same gravity, same drag, same
 * eight-block attraction, same five-minute lifetime.
 *
 * <p>Two deliberate simplifications. There is <strong>no merging</strong>: an orb is
 * worth one to three Essence, so combining them buys nothing and it is exactly the
 * mechanism described above. And there is <strong>no health</strong>: vanilla orbs
 * can be destroyed by fire and explosions, which for a progression resource is a way
 * to lose earned progress to something the player cannot see coming.
 */
public class LifeEssenceOrb extends Entity {
	/**
	 * The Essence this orb carries. Synched because the client picks the icon from
	 * it — a bigger orb is drawn as a bigger orb, with no packet of our own.
	 */
	private static final EntityDataAccessor<Integer> DATA_VALUE =
			SynchedEntityData.defineId(LifeEssenceOrb.class, EntityDataSerializers.INT);

	/**
	 * What an orb is worth when nobody said.
	 *
	 * <p>One rather than zero, and that matters: an orb spawned through vanilla's
	 * {@code /summon grandcraft:life_essence_orb} is built by the plain
	 * {@code (EntityType, Level)} constructor, which never runs the drop path and so
	 * never sets a value. At zero such an orb flew to the player, was collected, and
	 * granted nothing — indistinguishable from the whole system being broken, and it
	 * was reported as exactly that.
	 *
	 * <p>Used as the fallback in both {@link #defineSynchedData} and
	 * {@link #readAdditionalSaveData}, so the two cannot disagree.
	 * {@code /summon grandcraft:life_essence_orb ~ ~ ~ {Value:3}} still works, because
	 * vanilla applies summon NBT through the save-data path.
	 */
	private static final int DEFAULT_VALUE = 1;

	/** Five minutes, matching vanilla's orb, after which an uncollected orb expires. */
	private static final int LIFETIME_TICKS = 6000;

	/** How close a player must be before a resting orb starts moving towards them. */
	private static final double ATTRACT_RANGE = 8.0;

	/**
	 * How far the orb will chase a player it has already latched onto, squared. Larger
	 * than {@link #ATTRACT_RANGE} squared so an orb already in flight is not dropped
	 * the instant the player takes a step away.
	 */
	private static final double FOLLOW_RANGE_SQR = 64.0;

	/** Vanilla's orb figures, reused so these fall and settle like something familiar. */
	private static final double GRAVITY = 0.03;
	private static final float AIR_DRAG = 0.98F;
	private static final double BOUNCE_RETENTION = 0.4;
	private static final double ATTRACT_STRENGTH = 0.1;

	/** How many ticks a player must wait between pickups, as vanilla does for orbs. */
	private static final int PICKUP_DELAY_TICKS = 2;

	/**
	 * The number of distinct icons across the texture, which is a 4x4 grid exactly like
	 * vanilla's. Only the first few are reachable at present — an orb is worth at most
	 * {@link LevelSettings#MAX_ORB_VALUE} — and the rest are headroom for the boss and
	 * per-mob drops the design expects later.
	 */
	public static final int ICON_COUNT = 16;

	private final InterpolationHandler interpolation = new InterpolationHandler(this);

	private int age;

	/** The player this orb is flying towards, or null while it is at rest. */
	private Player followingPlayer;

	public LifeEssenceOrb(EntityType<? extends LifeEssenceOrb> type, Level level) {
		super(type, level);
	}

	public LifeEssenceOrb(Level level, double x, double y, double z, int value) {
		this(GrandCraftEntities.LIFE_ESSENCE_ORB, level);
		setPos(x, y, z);

		// A random heading and a small upward hop, so a group of orbs from one kill
		// scatters instead of stacking in a single column.
		setYRot(this.random.nextFloat() * 360.0F);
		setDeltaMovement(
				(this.random.nextDouble() * 0.2 - 0.1) * 2.0,
				this.random.nextDouble() * 0.2 * 2.0,
				(this.random.nextDouble() * 0.2 - 0.1) * 2.0);

		setValue(value);
	}

	/** Drops one orb of the given value into the world. */
	public static void award(ServerLevel level, Vec3 position, int value) {
		if (value <= 0) {
			return;
		}

		level.addFreshEntity(new LifeEssenceOrb(level, position.x, position.y, position.z, value));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_VALUE, DEFAULT_VALUE);
	}

	@Override
	public InterpolationHandler getInterpolation() {
		return this.interpolation;
	}

	@Override
	protected double getDefaultGravity() {
		return GRAVITY;
	}

	@Override
	protected Entity.MovementEmission getMovementEmission() {
		// Silent and scentless: an orb should not set off sculk sensors or leave
		// footstep sounds while it drifts towards the player.
		return Entity.MovementEmission.NONE;
	}

	@Override
	public void tick() {
		this.interpolation.interpolate();
		super.tick();

		if (isEyeInFluid(FluidTags.WATER)) {
			applyWaterMovement();
		} else {
			applyGravity();
		}

		followNearbyPlayer();

		Vec3 movement = getDeltaMovement();
		double verticalBefore = movement.y;

		move(MoverType.SELF, movement);
		applyEffectsFromBlocks();

		float drag = AIR_DRAG;

		if (onGround()) {
			drag *= level().getBlockState(getBlockPosBelowThatAffectsMyMovement())
					.getBlock().getFriction();
		}

		setDeltaMovement(getDeltaMovement().scale(drag));

		// A small bounce off the ground, but only from a real fall — the gravity test
		// is what stops an orb resting on a block from jittering forever.
		if (this.verticalCollisionBelow && verticalBefore < -getGravity()) {
			setDeltaMovement(
					getDeltaMovement().x,
					-verticalBefore * BOUNCE_RETENTION,
					getDeltaMovement().z);
		}

		this.age++;

		if (this.age >= LIFETIME_TICKS) {
			discard();
		}
	}

	/** Vanilla's orb behaviour in water: sideways drag, and a slow drift upward. */
	private void applyWaterMovement() {
		Vec3 movement = getDeltaMovement();

		setDeltaMovement(
				movement.x * 0.99,
				Math.min(movement.y + 0.0005, 0.06),
				movement.z * 0.99);
	}

	/**
	 * Picks a nearby player to fly to, and accelerates towards them.
	 *
	 * <p>The pull grows as the orb closes, which is what makes collection feel like
	 * being drawn in rather than like a constant-speed slide. Aimed at half the
	 * player's eye height rather than their feet, so orbs arrive at the body.
	 *
	 * <p>A spectator is never a target, and neither is a dying player: an orb should
	 * not chase someone who cannot collect it.
	 */
	private void followNearbyPlayer() {
		if (this.followingPlayer == null
				|| this.followingPlayer.isSpectator()
				|| this.followingPlayer.distanceToSqr(this) > FOLLOW_RANGE_SQR) {
			Player nearest = level().getNearestPlayer(this, ATTRACT_RANGE);

			this.followingPlayer = nearest != null && !nearest.isSpectator() && !nearest.isDeadOrDying()
					? nearest
					: null;
		}

		if (this.followingPlayer == null) {
			return;
		}

		Vec3 toPlayer = new Vec3(
				this.followingPlayer.getX() - getX(),
				this.followingPlayer.getY() + this.followingPlayer.getEyeHeight() / 2.0 - getY(),
				this.followingPlayer.getZ() - getZ());

		double pull = 1.0 - Math.sqrt(toPlayer.lengthSqr()) / ATTRACT_RANGE;

		if (pull <= 0.0) {
			return;
		}

		setDeltaMovement(getDeltaMovement()
				.add(toPlayer.normalize().scale(pull * pull * ATTRACT_STRENGTH)));
	}

	/**
	 * Collects the orb.
	 *
	 * <p>Server side only — the client is told about the pickup by the take animation
	 * and about the result by the attachment's own sync, so it never adds Essence for
	 * itself.
	 *
	 * <p>A sound is played on every pickup rather than only on a level-up. Essence
	 * Power has no HUD bar by design, so without one the orb would vanish with no
	 * confirmation that anything was gained at all.
	 */
	@Override
	public void playerTouch(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		if (player.takeXpDelay > 0) {
			return;
		}

		player.takeXpDelay = PICKUP_DELAY_TICKS;
		player.take(this, 1);

		EssenceAwards.award(serverPlayer, getValue());

		// Pitched below vanilla's own orb chime so the two are distinguishable when
		// both kinds of orb are on the ground together, which after any fight they are.
		level().playSound(null, getX(), getY(), getZ(),
				SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4F, 0.6F);

		discard();
	}

	/**
	 * Orbs cannot be destroyed.
	 *
	 * <p>A departure from vanilla, which gives its orbs five health so fire and
	 * explosions can pop them. Essence is earned progress, and losing it to a creeper
	 * that happened to go off nearby is a punishment the player cannot see coming and
	 * cannot avoid. Falling out of the world still removes them, as it does anything.
	 */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean hurtClient(DamageSource source) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putShort("Age", (short) this.age);
		output.putShort("Value", (short) getValue());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.age = input.getShortOr("Age", (short) 0);

		// Floored at one, so neither a summon without NBT nor a hand-edited zero can
		// produce an orb that is collected and grants nothing.
		setValue(Math.max(input.getShortOr("Value", (short) DEFAULT_VALUE), DEFAULT_VALUE));
	}

	public int getValue() {
		return getEntityData().get(DATA_VALUE);
	}

	private void setValue(int value) {
		getEntityData().set(DATA_VALUE, value);
	}

	/**
	 * Which of the sixteen icons on the sheet this orb is drawn with.
	 *
	 * <p>One icon per point of Essence for the values that exist today, which keeps
	 * the mapping obvious while there are only three of them. Vanilla's equivalent is
	 * a table of thresholds because its orbs run to thousands; ours can stay simple
	 * until boss drops give it a reason not to.
	 */
	public int getIcon() {
		return Math.clamp(getValue() - 1, 0, ICON_COUNT - 1);
	}
}
