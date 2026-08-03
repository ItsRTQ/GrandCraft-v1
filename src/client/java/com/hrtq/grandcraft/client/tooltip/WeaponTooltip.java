package com.hrtq.grandcraft.client.tooltip;

import com.hrtq.grandcraft.client.ClientStatSettings;
import com.hrtq.grandcraft.client.ClientWeaponSettings;
import com.hrtq.grandcraft.combat.MeleeDamage;
import com.hrtq.grandcraft.combat.WeaponCategory;
import com.hrtq.grandcraft.stats.CharacterStat;
import com.hrtq.grandcraft.stats.StatEffects;
import com.hrtq.grandcraft.stats.StatWeights;
import com.hrtq.grandcraft.stats.WeaponRequirement;
import com.hrtq.grandcraft.stats.WeaponScaling;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Says what a weapon will actually do in <em>this</em> character's hands.
 *
 * <p>Vanilla's "7 Attack Damage" is now a lie — the same sword deals wildly different
 * damage depending on who is holding it, and vanilla's line reports a number nothing
 * reads any more. That block is suppressed by {@code ItemStackAttributeTooltipMixin};
 * this is what goes in its place.
 *
 * <p>Shipped in the same body of work as the mechanic rather than after it, and not as
 * a nicety: a requirement is the one part of this system that is a <em>decision</em>
 * rather than a number, and a decision the player cannot see before they commit to it
 * is not a decision at all. Finding out that a claymore is not for you by watching a
 * zombie shrug off eight swings is the failure mode this exists to prevent.
 *
 * <h2>Reaches the same answer as the server by calling the same method</h2>
 * Every line here comes out of {@link MeleeDamage#scalingFor}, the method the swing
 * itself goes through, with the settings handed in from the client's synced copies. A
 * second implementation that agreed today would drift the first time either side moved.
 */
public final class WeaponTooltip {

	private WeaponTooltip() {
	}

	public static void register() {
		ItemTooltipCallback.EVENT.register(WeaponTooltip::append);
	}

	private static void append(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
			TooltipFlag flag, List<Component> lines) {
		LocalPlayer player = Minecraft.getInstance().player;

		// No player means a tooltip drawn outside a world — a pack screen, a recipe book
		// in some future context. There is no character for the answer to be about.
		if (player == null || stack.isEmpty()) {
			return;
		}

		if (!describes(stack)) {
			return;
		}

		WeaponScaling scaling = MeleeDamage.scalingFor(player, stack,
				MeleeDamage.attributeTotalWith(player, stack),
				ClientWeaponSettings.current(), ClientStatSettings.current());

		StatWeights weights = ClientWeaponSettings.current()
				.forCategory(WeaponCategory.of(stack)).weights();

		lines.add(Component.empty());
		lines.add(Component.translatable("item.modifiers.mainhand").withStyle(ChatFormatting.GRAY));
		lines.add(damageLine(scaling));
		lines.add(indent(Component.translatable("tooltip.grandcraft.weapon.scales", describe(weights))
				.withStyle(ChatFormatting.DARK_GRAY)));

		if (scaling.requirement().exists()) {
			lines.add(requirementLine(player, scaling.requirement()));
		}

		// Advanced tooltips get the working. Behind F3+H rather than always shown,
		// because "base times multiplier" is how someone tuning the system reads it and
		// noise to everyone else.
		if (flag.isAdvanced()) {
			lines.add(indent(Component.translatable("tooltip.grandcraft.weapon.detail",
					number(scaling.base()), number(scaling.multiplier()))
					.withStyle(ChatFormatting.DARK_GRAY)));
		}
	}

	/**
	 * Whether this stack is something a player would swing at all.
	 *
	 * <p>Two ways in, because they catch different things. A tag claims the weapons the
	 * ruleset has an opinion about, including any a datapack has added; an attack-damage
	 * contribution catches everything else that vanilla itself considered a weapon
	 * enough to print a damage line for — a pickaxe, a shovel, a trident.
	 *
	 * <p>That second case is the one worth being deliberate about: swinging a pickaxe
	 * now deals stat-scaled damage like anything else, so hiding the block there would
	 * leave a tool showing nothing where vanilla showed a number, and the player would
	 * reasonably conclude it does nothing.
	 */
	public static boolean describes(ItemStack stack) {
		return WeaponCategory.of(stack) != WeaponCategory.UNARMED
				|| MeleeDamage.itemAttackDamage(stack) > 0.0;
	}

	private static Component damageLine(WeaponScaling scaling) {
		if (!scaling.meetsRequirement()) {
			return indent(Component.translatable("tooltip.grandcraft.weapon.damage_unmet",
					number(scaling.damage())).withStyle(ChatFormatting.RED));
		}

		// Blue is vanilla's own colour for a positive attribute line, so the eye lands
		// where it always did even though the number means something new.
		return indent(Component.translatable("tooltip.grandcraft.weapon.damage",
				number(scaling.damage())).withStyle(ChatFormatting.BLUE));
	}

	/**
	 * The requirement, and whether this character meets it.
	 *
	 * <p>The unmet form names the value they actually have. "Requires 14 Strength" tells
	 * someone they have failed; "Requires 14 Strength — you have 7" tells them how far
	 * off they are and therefore whether it is worth working towards, which is the
	 * question a player is really asking.
	 */
	private static Component requirementLine(LocalPlayer player, WeaponRequirement requirement) {
		if (requirement.isMetBy(player)) {
			return indent(Component.translatable("tooltip.grandcraft.weapon.requires",
					requirement.value(), requirement.stat().displayName())
					.withStyle(ChatFormatting.GREEN));
		}

		double held = StatEffects.statOf(player, requirement.stat().attribute());

		return indent(Component.translatable("tooltip.grandcraft.weapon.requires_unmet",
				requirement.value(), requirement.stat().displayName(), number(held))
				.withStyle(ChatFormatting.RED));
	}

	/** "60% Strength, 40% Agility", in the record's own declaration order. */
	private static Component describe(StatWeights weights) {
		int total = weights.total();

		if (total <= 0) {
			return CharacterStat.STRENGTH.displayName();
		}

		List<Component> parts = new ArrayList<>();

		for (CharacterStat stat : CharacterStat.values()) {
			int weight = weights.weightOf(stat);

			if (weight > 0) {
				parts.add(Component.translatable("tooltip.grandcraft.weapon.weight",
						Math.round(weight * 100.0 / total), stat.displayName()));
			}
		}

		MutableComponent joined = Component.empty();

		for (int index = 0; index < parts.size(); index++) {
			if (index > 0) {
				joined.append(", ");
			}

			joined.append(parts.get(index));
		}

		return joined;
	}

	/**
	 * Vanilla indents every attribute line under its slot heading with a single space,
	 * so ours do too — the block has to look like it belongs where it is.
	 */
	private static MutableComponent indent(Component line) {
		return Component.literal(" ").append(line);
	}

	/** Trims a pointless ".0" so a whole value does not read like a measurement. */
	private static String number(double value) {
		return value == Math.rint(value)
				? Long.toString((long) value)
				: String.format(Locale.ROOT, "%.1f", value);
	}
}
