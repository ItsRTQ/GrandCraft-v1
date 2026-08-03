package com.hrtq.grandcraft.combat;

import com.hrtq.grandcraft.item.GrandCraftComponents;
import com.hrtq.grandcraft.stats.StatSettings;
import com.hrtq.grandcraft.stats.StatTuning;
import com.hrtq.grandcraft.stats.StatWeights;
import com.hrtq.grandcraft.stats.WeaponRequirement;
import com.hrtq.grandcraft.stats.WeaponScaling;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Where a weapon and a character meet.
 *
 * <p>{@code Weapons} answers "what does holding this cost and how long does it commit
 * me for"; this answers "and how hard does it hit". The two are deliberately separate
 * classes because they are read at different moments — one when a swing starts, one
 * when it lands — and because only this one has any business knowing about stats.
 *
 * <h2>One resolution, two callers</h2>
 * {@link #scalingFor} takes every input explicitly and touches no global state, so the
 * server's swing and the client's tooltip reach the same number by <em>calling the same
 * method</em> rather than by two implementations agreeing with each other. That is the
 * whole reason it is shaped this way; a tooltip that disagrees with the damage dealt is
 * worse than no tooltip, because it is believed.
 *
 * <h2>Mobs are deliberately excluded</h2>
 * {@link #appliesTo} gates on {@code CombatVerb.WEAPONS}, which only the player has.
 * Mobs express their power through {@code RolledStats} instead, and that is not an
 * oversight to be tidied up later: a requirement a player cannot see is a mechanic they
 * cannot read, and a zombie standing there dealing one damage because it failed a gate
 * nobody told it about would be indistinguishable from a broken attack.
 */
public final class MeleeDamage {

	private MeleeDamage() {
	}

	/**
	 * Whether this entity's melee damage is ours to compute.
	 *
	 * <p>The same gate {@code CombatController.weaponProfile} uses, asked the same way,
	 * so the two can never disagree about who is on the weapon path.
	 *
	 * <p><strong>Server side only, and not merely as a formality.</strong>
	 * {@code Player.attack} also runs on the client, where {@code MultiPlayerGameMode}
	 * calls it to predict the hit visuals. Both {@code CombatProfiles} and the two
	 * tuning holders are server-thread state — and in single player the integrated
	 * server shares these very classes, so answering here off the render thread would
	 * race the rebuild inside {@code CombatProfiles}. The prediction keeping vanilla's
	 * number costs nothing: the server's damage is the authoritative one, and the client
	 * is only drawing a reaction.
	 *
	 * <p>Note the tooltip does <em>not</em> come through here. It calls
	 * {@link #scalingFor} with settings handed in explicitly, which is exactly why that
	 * method reads no globals.
	 */
	public static boolean appliesTo(LivingEntity entity) {
		if (entity.level().isClientSide()) {
			return false;
		}

		CombatProfile profile = CombatProfiles.forEntity(entity);
		return profile != null && profile.usesWeapons();
	}

	/**
	 * What this character's swing with this weapon is worth, using the live settings.
	 *
	 * <p>Server-side convenience. {@code attributeTotal} is read from the entity rather
	 * than passed in, which is correct here and only here: the entity really is holding
	 * the stack.
	 */
	public static WeaponScaling forSwing(Player player, ItemStack stack) {
		return scalingFor(player, stack, player.getAttributeValue(Attributes.ATTACK_DAMAGE),
				WeaponTuning.current(), StatTuning.current());
	}

	/**
	 * The whole rule, with nothing read from a global.
	 *
	 * @param attributeTotal the finished {@code ATTACK_DAMAGE} for this entity holding
	 *     this stack. Passed in rather than read because the client has to ask the
	 *     question about a weapon in a bag, which the attribute system cannot answer.
	 */
	public static WeaponScaling scalingFor(LivingEntity entity, ItemStack stack,
			double attributeTotal, WeaponSettings weapons, StatSettings stats) {
		WeaponCategory category = WeaponCategory.of(stack);
		StatWeights weights = weapons.forCategory(category).weights();
		WeaponRules rules = weapons.rules();

		double fromItem = itemAttackDamage(stack);
		float base = (float) (attributeTotal - fromItem * (1.0 - rules.baseScale()));

		// Derived from what the weapon itself is worth, NOT from the finished attribute.
		// Those differ by every modifier the holder happens to be carrying, and gating on
		// the total would mean a Strength potion *raised* a sword's requirement — drink
		// one and your own weapon locks you out. The intrinsic figure is the one a
		// requirement is a statement about.
		WeaponRequirement requirement = requirementOf(stack,
				entity.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) + fromItem, weights);

		return WeaponScaling.of(base, rules.baseScale(), requirement, weights.blend(entity),
				requirement.isMetBy(entity), rules.failedDamage(), stats);
	}

	/**
	 * The finished attack damage this player would have with the given stack in hand.
	 *
	 * <p>Exists for the tooltip, which has to answer for a weapon sitting in a bag: swap
	 * out what is actually held and swap in what is being hovered. For a stack that
	 * really is in the main hand it returns exactly {@code getAttributeValue}, so the
	 * two callers cannot drift.
	 */
	public static double attributeTotalWith(Player player, ItemStack stack) {
		return player.getAttributeValue(Attributes.ATTACK_DAMAGE)
				- itemAttackDamage(player.getMainHandItem())
				+ itemAttackDamage(stack);
	}

	/**
	 * Tells the player what just happened to their swing.
	 *
	 * <p>A weapon dropping to one damage is a large, deliberate event with nothing on
	 * screen to mark it — no animation changes, the hit still lands, the health bar just
	 * barely moves. Shipped in the same slice as the mechanic rather than after it,
	 * because a state that cannot be perceived gets reported as a bug and costs a round
	 * of debugging the wrong thing.
	 *
	 * <p>Deliberately not a message or a title: this fires on every swing with the wrong
	 * weapon, and anything louder than a sound would become noise within a minute. The
	 * tooltip is where the explanation lives.
	 */
	public static void reportSwing(Player player, WeaponScaling scaling) {
		if (scaling.meetsRequirement()) {
			return;
		}

		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4F, 0.5F);
	}

	/**
	 * What the item itself adds to {@code ATTACK_DAMAGE} in the main hand.
	 *
	 * <p>This is the number the down-scale is applied to, and it is written as a
	 * subtraction from the total rather than a multiplication of it because the two
	 * differ exactly where it matters. A player's base {@code ATTACK_DAMAGE} of 1 is
	 * <em>theirs</em>, not the weapon's, so bare hands come out at 1.0 rather than half
	 * of it, with no special case. Anything else riding on the attribute that did not
	 * come from the item — a Strength potion, say — passes through undiminished and is
	 * then multiplied by the character's own curve, which is the right treatment for it.
	 *
	 * <p>{@code compute} matches the attribute by reference on the {@code Holder}, so
	 * the constant has to be {@code Attributes.ATTACK_DAMAGE} itself and not a lookup
	 * that happens to resolve to the same attribute.
	 */
	public static double itemAttackDamage(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0.0;
		}

		return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
				.compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND);
	}

	/**
	 * What this weapon demands, authored if it says so and derived if it does not.
	 *
	 * <p>The component is checked first so a weapon can always overrule the formula, and
	 * the formula exists so that no weapon <em>has</em> to. Anything the item tags do not
	 * claim as a weapon still passes through here — a pickaxe, a torch, an empty hand —
	 * and lands on a small or zero requirement naturally, because the derivation reads
	 * the damage the thing actually does.
	 *
	 * <p>The stat demanded comes from the weights rather than from a field of its own, so
	 * a weapon can never gate on something it does not scale off.
	 */
	private static WeaponRequirement requirementOf(ItemStack stack, double attributeTotal,
			StatWeights weights) {
		if (stack != null && !stack.isEmpty()) {
			WeaponRequirement authored = stack.get(GrandCraftComponents.WEAPON_REQUIREMENT);

			if (authored != null) {
				return authored;
			}
		}

		int derived = (int) Math.round(
				WeaponConstants.REQUIREMENT_SLOPE * attributeTotal - WeaponConstants.REQUIREMENT_OFFSET);

		return derived <= 0 ? WeaponRequirement.NONE : new WeaponRequirement(weights.dominant(), derived);
	}
}
