package com.hrtq.grandcraft.item;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.stats.CharacterStat;
import com.hrtq.grandcraft.stats.WeaponRequirement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Weapon;

/**
 * The mod's own items.
 *
 * <h2>What lives on an item, and what does not</h2>
 * Damage, attack speed, reach and durability are properties of <em>this weapon</em>
 * and live here as vanilla data components. They have to be per-instance, because
 * enchantments, smithing and every future magic weapon modify them per-instance,
 * and putting them in the component system means vanilla's tooltip and the
 * attribute stack read them for free.
 *
 * <p>Endlag and stamina cost are <em>not</em> here. Those describe how GrandCraft
 * treats a whole class of weapon — they apply identically to a vanilla iron sword
 * and to a sword from a mod this one has never heard of — so they belong to the
 * weapon category and its config, not to any one item.
 *
 * <h2>The damage numbers below are no longer the damage dealt</h2>
 * {@code MeleeDamage} rebuilds a player's melee damage out of their stats: the item's
 * {@code ATTACK_DAMAGE} is down-scaled to a low base and multiplied by what the
 * character brings. So the figures here set a weapon's <em>place in the ladder</em>
 * rather than its output — the claymore hits harder than the dagger because the number
 * here is bigger, but how much harder is the holder's business.
 *
 * <p>They also decide what the weapon <em>demands</em>: a requirement is derived from
 * this number for anything that does not state one. All three weapons here state one,
 * because a weapon the mod ships should not have its gate move because someone retuned
 * its damage.
 *
 * <p>Attack speed is now purely a rhythm control. It used to be dangerous to lower —
 * vanilla scaled damage by the attack cooldown while the mod's own indicator showed
 * something else, so a slow weapon widened the window where the crosshair and the
 * server disagreed. {@code PlayerAttackMixin} deletes that curve, so the trap is gone;
 * the band below is kept because it reads well, not because it has to be.
 */
public final class GrandCraftItems {
	/**
	 * Everything registered here, in declaration order, for the creative tab.
	 *
	 * <p><strong>Declared before any item below it.</strong> Static initialisers run
	 * in source order and each item's initialiser writes into this list, so a field
	 * declared afterwards would still be null and the class would fail to
	 * initialise. Same trap as {@code GrandCraftEntities.SUMMONABLE}.
	 */
	private static final List<Item> ALL = new ArrayList<>();

	/**
	 * Heavy: reach and commitment.
	 *
	 * <p>Reach 4.0 against the vanilla 3.0 — two moves clear, because 3.2 would be
	 * indistinguishable from noise. That also puts it outside a zombie's own ~1.4
	 * block melee, so a claymore can open on an approach for free. That is the
	 * category's identity rather than a bug, and it is the first number to cut if
	 * heavy weapons turn out to dominate.
	 */
	public static final Item CLAYMORE = register("claymore", new Item.Properties()
			.sword(ToolMaterial.IRON, 5.0F, -2.6F)
			.durability(400)
			.component(DataComponents.ATTACK_RANGE, reach(4.0F))
			.component(GrandCraftComponents.WEAPON_REQUIREMENT, requires(CharacterStat.STRENGTH, 14)));

	/**
	 * Light: speed and cheapness.
	 *
	 * <p>Attack speed is deliberately 1.8 rather than a round 1.6 or 2.0. At 2.0 the
	 * cooldown is exactly 10 ticks, which is exactly the stamina regen delay — a
	 * threshold the engine parks on, so the dagger would feel free or not-free at
	 * random depending on click jitter. At 1.8 (11.1 ticks) it earns about one tick
	 * of regen per swing: a small, legible reward for rhythm.
	 */
	public static final Item DAGGER = register("dagger", new Item.Properties()
			.sword(ToolMaterial.IRON, 2.0F, -2.2F)
			.durability(180)
			.component(DataComponents.ATTACK_RANGE, reach(2.4F))
			.component(GrandCraftComponents.WEAPON_REQUIREMENT, requires(CharacterStat.AGILITY, 12)));

	/**
	 * Arcane: the caster's implement.
	 *
	 * <p>Hand-built attributes rather than {@code Properties.sword(...)}, which would
	 * drag in a tool material's durability and need a negative damage argument to
	 * land on 3 total. This is also the shape the next weapon with unusual stats
	 * will want.
	 *
	 * <p>No {@code ATTACK_RANGE} component: melee reach is not the staff's job, so
	 * it takes the vanilla 3.0 like a sword.
	 *
	 * <p>The {@code WEAPON} component is what makes it lose durability when it hits
	 * something, and hand-building the properties is exactly how it went missing the
	 * first time: {@code Properties.sword(...)} adds it for free, and this does not
	 * use that. Despite the name the component does not mean "is a weapon" — it means
	 * "wears when it attacks", which is why vanilla puts it on pickaxes too.
	 */
	public static final Item STAFF = register("staff", StaffItem::new, new Item.Properties()
			.durability(250)
			.enchantable(15)
			.component(DataComponents.WEAPON, new Weapon(1))
			.component(GrandCraftComponents.WEAPON_REQUIREMENT, requires(CharacterStat.ARCANE, 12))
			.attributes(ItemAttributeModifiers.builder()
					.add(Attributes.ATTACK_DAMAGE,
							new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 2.0,
									AttributeModifier.Operation.ADD_VALUE),
							EquipmentSlotGroup.MAINHAND)
					.add(Attributes.ATTACK_SPEED,
							new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.8,
									AttributeModifier.Operation.ADD_VALUE),
							EquipmentSlotGroup.MAINHAND)
					.build()));

	/**
	 * The mod's own creative tab.
	 *
	 * <p>Its own rather than a vanilla one because every {@code CreativeModeTabs}
	 * key is private, so reaching {@code COMBAT} would mean rebuilding the
	 * {@code ResourceKey} by hand anyway — and an RPG expansion is going to keep
	 * adding items.
	 *
	 * <p><strong>Declared last.</strong> The icon supplier reads {@link #CLAYMORE},
	 * and a field declared above it would still be null when this initialiser runs.
	 */
	public static final CreativeModeTab TAB = Registry.register(
			BuiltInRegistries.CREATIVE_MODE_TAB, GrandCraft.id("grandcraft"),
			FabricCreativeModeTab.builder()
					.title(Component.translatable("itemGroup.grandcraft"))
					.icon(() -> new ItemStack(CLAYMORE))
					// Explicit lambda rather than a method reference: Output overloads
					// accept for both ItemStack and ItemLike, and spelling it out keeps
					// the resolution obvious.
					.displayItems((parameters, output) -> {
						for (Item item : ALL) {
							output.accept(item);
						}
					})
					.build());

	private GrandCraftItems() {
	}

	/**
	 * A weapon's melee reach, in the shape vanilla's own {@code AttackRange.defaultFor}
	 * builds: no minimum, no hitbox margin, and mobs reaching exactly as far as
	 * players do.
	 *
	 * <p>Survival and creative are given the same figure deliberately. Vanilla's
	 * default does the same, and a creative-only reach bonus would mean the mode the
	 * numbers are usually tested in is not the mode they apply to.
	 *
	 * <p>This is the one number here that cannot be tuned at runtime: default
	 * components are baked at registration, before any config file is read. Changing
	 * it costs a rebuild.
	 */
	private static AttackRange reach(float blocks) {
		return new AttackRange(0.0F, blocks, 0.0F, blocks, 0.0F, 1.0F);
	}

	/**
	 * What a character needs before this weapon works properly in their hands.
	 *
	 * <p>Stated rather than derived for all three of the mod's weapons, so that retuning
	 * a damage number never silently moves a gate. The stat named here should always be
	 * the one its category actually scales off, or the weapon demands one thing and
	 * rewards another.
	 *
	 * <p>Baked at registration like {@link #reach}, so it costs a rebuild to change.
	 */
	private static WeaponRequirement requires(CharacterStat stat, int value) {
		return new WeaponRequirement(stat, value);
	}

	private static Item register(String name, Item.Properties properties) {
		return register(name, Item::new, properties);
	}

	/**
	 * Registers one item and adds it to the creative tab.
	 *
	 * <p>{@code setId} is not optional in 26.2 — vanilla's own {@code registerItem}
	 * calls it before the item is constructed, and the description id and the model
	 * path are both derived from it.
	 */
	private static Item register(String name, Function<Item.Properties, Item> factory,
			Item.Properties properties) {
		Identifier id = GrandCraft.id(name);
		Item item = Registry.register(BuiltInRegistries.ITEM, id,
				factory.apply(properties.setId(ResourceKey.create(Registries.ITEM, id))));

		ALL.add(item);
		return item;
	}

	/** No-op: exists to force this class to initialise from {@code onInitialize}. */
	public static void register() {
	}
}
