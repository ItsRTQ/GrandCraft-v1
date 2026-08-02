package com.hrtq.grandcraft.entity;

import com.hrtq.grandcraft.combat.GrandCraftDamageTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The staff's default attack: a short punch of wind.
 *
 * <p>Extends {@link ThrowableItemProjectile} purely to inherit the "renders as an
 * item sprite" plumbing, which is what lets it borrow the wind charge's look for one
 * line of client code and no artwork.
 *
 * <p><strong>Deliberately not a {@code WindCharge}.</strong> That class explodes
 * through an {@code ExplosionDamageCalculator} and carries deflection rules, none of
 * which is wanted here — this is a small, cheap, disruptive poke, not an explosion.
 * Borrowing the sprite is not the same as borrowing the behaviour.
 *
 * <h2>Flat flight, and why that makes the lifetime load-bearing</h2>
 * Gravity is zero so a gust flies straight rather than lobbing like a snowball, which
 * is what makes it feel like a projected force. The consequence is that one which
 * hits nothing <em>never lands</em>, and a projectile that never lands never
 * despawns: without the age cap in {@link #tick()} this leaks one entity per missed
 * cast, and the symptom is not a leak but unexplained lag. The cap is configured, not
 * hard-coded, but it is a correctness bound rather than a flavour value.
 */
public class GustProjectile extends ThrowableItemProjectile {
	private float damage = DEFAULT_DAMAGE;
	private float knockback = DEFAULT_KNOCKBACK;
	private int lifetimeTicks = DEFAULT_LIFETIME_TICKS;
	private int age;

	/**
	 * What an unconfigured gust does, and <strong>these must not be zero</strong>.
	 *
	 * <p>A {@code /summon} builds the entity through the plain
	 * {@code (EntityType, Level)} constructor, which never runs the cast path, so a
	 * summoned gust is an unconfigured one. This mod has already paid for that
	 * exact mistake once: the essence orb defaulted to a value of zero, and a
	 * summoned orb granted nothing, which was reported as the whole progression
	 * system being broken. A summoned gust that did no damage and shoved nobody
	 * would read the same way — and summoning is precisely how this entity is
	 * meant to be tested before the staff can fire it.
	 *
	 * <p>They match the shipped arcane defaults so a summoned gust and a cast one
	 * behave alike.
	 */
	private static final float DEFAULT_DAMAGE = 2.0F;

	/** Matches the shipped arcane default: a gust disrupts by hitting, not by shoving. */
	private static final float DEFAULT_KNOCKBACK = 0.0F;
	private static final int DEFAULT_LIFETIME_TICKS = 20;

	/** Vanilla's own event id for "a thrown item broke", which draws the particles. */
	private static final byte EVENT_BURST = 3;

	public GustProjectile(EntityType<? extends GustProjectile> type, Level level) {
		super(type, level);
	}

	/**
	 * The casting form.
	 *
	 * <p><strong>The caster's weapon is deliberately not passed through.</strong>
	 * {@code ThrowableItemProjectile} stores whatever stack it is handed and renders
	 * exactly that, with no fallback to {@link #getDefaultItem()} — and the
	 * {@code ItemStack} that {@code Projectile.spawnProjectileFromRotation} hands its
	 * factory is <em>the weapon being cast with</em>, not the thing being thrown. The
	 * two coincide for a snowball, which is why vanilla forwards it; here they do not,
	 * and forwarding it sent a spinning staff downrange instead of a gust.
	 */
	public GustProjectile(Level level, LivingEntity owner) {
		super(GrandCraftEntities.GUST, owner, level, new ItemStack(Items.WIND_CHARGE));
	}

	/** Configured by the cast; see {@code ArcaneSettings}. */
	public void configure(float damage, float knockback, int lifetimeTicks) {
		this.damage = damage;
		this.knockback = knockback;
		this.lifetimeTicks = Math.max(1, lifetimeTicks);
	}

	/**
	 * The wind charge's sprite, borrowed for its look alone. Nothing else about this
	 * entity is a wind charge.
	 */
	@Override
	protected Item getDefaultItem() {
		return Items.WIND_CHARGE;
	}

	/** A gust is thrown, not lobbed. See the note on the lifetime cap above. */
	@Override
	protected double getDefaultGravity() {
		return 0.0;
	}

	@Override
	public void tick() {
		super.tick();

		// Not cosmetic: with no gravity this is the only thing that ever removes a
		// gust that hit nothing. Checked after super.tick() so a gust that hit
		// something has already discarded itself and cannot be counted twice.
		if (!level().isClientSide() && ++this.age > this.lifetimeTicks) {
			discard();
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);

		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}

		Entity target = result.getEntity();
		DamageSource source = new DamageSource(
				serverLevel.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
						.getOrThrow(GrandCraftDamageTypes.GUST),
				this, getOwner());

		// Gated on the hit actually landing. A guard and a dodge both veto through
		// ALLOW_DAMAGE, and the return value is the only signal that happened — so
		// without this a blocked or dodged gust would still shove its target, which
		// would read as the defence half-working.
		if (target instanceof LivingEntity living && living.hurtServer(serverLevel, source, this.damage)) {
			push(living);
		}
	}

	/**
	 * Shoves the target along the gust's own direction of travel.
	 *
	 * <p>Uses {@code setDeltaMovement} plus {@code hurtMarked} rather than
	 * {@code LivingEntity.knockback}, matching what the rest of this mod already does
	 * for dodges and stagger: server-applied movement only reaches the client when
	 * that flag is set, and the knockback helper's parameter order is not readable
	 * from its descriptor.
	 */
	private void push(LivingEntity target) {
		Vec3 motion = getDeltaMovement();
		double horizontal = motion.x * motion.x + motion.z * motion.z;

		if (this.knockback <= 0.0F || !(horizontal > 0.0) || !Double.isFinite(horizontal)) {
			return;
		}

		double scale = this.knockback / Math.sqrt(horizontal);
		Vec3 current = target.getDeltaMovement();

		// Horizontal only: a gust that lifted its target would trade a disruption for
		// a free escape from anything melee.
		target.setDeltaMovement(current.x + motion.x * scale, current.y, current.z + motion.z * scale);
		target.hurtMarked = true;
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);

		if (level().isClientSide()) {
			return;
		}

		level().broadcastEntityEvent(this, EVENT_BURST);
		level().playSound(null, getX(), getY(), getZ(),
				SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.6F, 1.1F);
		discard();
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id != EVENT_BURST) {
			super.handleEntityEvent(id);
			return;
		}

		// An emitter draws its own shaped burst, so there is no velocity to get wrong.
		// That matters here: the one hand-tuned particle burst in this mod needed a
		// speed of 25 to be visible at all, because its provider quietly discarded the
		// velocity it was handed.
		level().addParticle(ParticleTypes.GUST_EMITTER_SMALL, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putFloat("Damage", this.damage);
		output.putFloat("Knockback", this.knockback);
		output.putInt("Lifetime", this.lifetimeTicks);
		output.putInt("Age", this.age);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.damage = input.getFloatOr("Damage", this.damage);
		this.knockback = input.getFloatOr("Knockback", this.knockback);

		// Floored at one for the same reason configure() floors it: a zero here would
		// be a gust that never expires.
		this.lifetimeTicks = Math.max(1, input.getIntOr("Lifetime", DEFAULT_LIFETIME_TICKS));
		this.age = input.getIntOr("Age", 0);
	}
}
