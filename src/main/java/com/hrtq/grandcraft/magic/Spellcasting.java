package com.hrtq.grandcraft.magic;

import com.hrtq.grandcraft.combat.ArcaneSettings;
import com.hrtq.grandcraft.combat.CombatController;
import com.hrtq.grandcraft.combat.GrandCraftCombat;
import com.hrtq.grandcraft.combat.WeaponCategory;
import com.hrtq.grandcraft.combat.WeaponTuning;
import com.hrtq.grandcraft.entity.GustProjectile;
import com.hrtq.grandcraft.stats.ArcaneScaling;
import com.hrtq.grandcraft.stats.ManaSettings;
import com.hrtq.grandcraft.stats.StatTuning;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

/**
 * Casting: the one entry point between an implement and a spell.
 *
 * <p>{@code StaffItem} names a spell and hands over; everything about whether the
 * cast is legal, what it costs and what it produces lives here. When spells become
 * selectable, the item reads the caster's choice instead of naming one and nothing in
 * that file changes.
 *
 * <p>Server-authoritative, like every other verb in this mod. There is no client
 * prediction and no packet of our own: the client's own {@code use} call returns
 * {@code FAIL} so it predicts nothing, vanilla's use packet reaches the server
 * regardless, and everything the player sees afterwards — the swing, the cooldown
 * sweep, the projectile, the mana bar — arrives as ordinary synced state.
 */
public final class Spellcasting {
	private Spellcasting() {
	}

	/**
	 * Attempts a cast.
	 *
	 * @return the result for {@code Item.use} to hand back; {@code FAIL} when nothing
	 *         was cast, so no cooldown or item-use side effect is applied.
	 */
	public static InteractionResult cast(ServerLevel level, ServerPlayer caster,
			InteractionHand hand, Spell spell) {
		ItemStack stack = caster.getItemInHand(hand);

		// Checked here rather than trusted to vanilla. The cooldown is enforced only in
		// MultiPlayerGameMode on the client — ItemStack.use never consults it — so
		// without this a client that simply kept sending use packets would cast at will.
		if (caster.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}

		ArcaneSettings arcane = WeaponTuning.current().forCategory(WeaponCategory.ARCANE).arcane();
		ManaSettings mana = StatTuning.current().mana();

		// A configured zero on either is that feature's off switch, and a cast must not
		// route around it — the same rule that stops a bought mana ceiling reviving a
		// pool an admin turned off.
		if (!arcane.enabled() || !mana.enabled()) {
			return InteractionResult.FAIL;
		}

		CombatController controller = GrandCraftCombat.controllerOf(caster);

		// Silent when it cannot be paid for (user's call, 2026-08-02: the fizzle sound
		// was too jarring). Defensible now in a way it would not have been a slice ago:
		// the mana bar is on the HUD, so an empty pool is visible rather than merely
		// inferred, and the refusal is not the invisible state that lesson warns about.
		// If "the staff randomly stops working" is ever reported, this is the cause and
		// the answer is a quieter cue rather than none.
		if (!controller.spendMana(caster, mana, arcane.manaCost())) {
			return InteractionResult.FAIL;
		}

		return switch (spell) {
			case GUST -> castGust(level, caster, stack, arcane);
		};
	}

	private static InteractionResult castGust(ServerLevel level, ServerPlayer caster,
			ItemStack stack, ArcaneSettings arcane) {
		// One scaling object for the whole cast, so damage and cooldown cannot be read
		// from two different snapshots of the caster's Arcane.
		ArcaneScaling scaling = ArcaneScaling.of(caster, StatTuning.current());
		float damage = scaling.damage(arcane.damage());

		// Configured inside the factory rather than on the returned entity, so the gust
		// is complete before it is spawned and there is no window in which a fully
		// live projectile is carrying its unconfigured defaults.
		Projectile.spawnProjectileFromRotation(
				// The third factory argument is the weapon being cast with, not the thing
				// thrown, so it is deliberately unused: a gust looks like a gust whatever
				// it was cast from.
				(spawnLevel, owner, weapon) -> {
					GustProjectile gust = new GustProjectile(spawnLevel, owner);
					gust.configure(damage, arcane.knockbackStrength(), arcane.rangeTicks());
					return gust;
				},
				level, stack, caster,
				0.0F, arcane.speed(), INACCURACY);

		// Arcane buys rate as well as force. The two compound deliberately, which is
		// what makes the staff worth far more to a caster who invested in it while
		// still leaving it usable by one who did not.
		caster.getCooldowns().addCooldown(stack, scaling.cooldown(arcane.cooldownTicks()));

		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS,
				0.7F, 0.9F + caster.getRandom().nextFloat() * 0.2F);

		// SUCCESS_SERVER rather than SUCCESS: the client already declined to predict
		// this, so the swing is the server's to broadcast — to the caster as much as to
		// everyone watching.
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Vanilla's own figure for a thrown item, so a gust scatters like a snowball. */
	private static final float INACCURACY = 1.0F;
}
