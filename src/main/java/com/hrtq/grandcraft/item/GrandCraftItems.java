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
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
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

	private static final int TICKS_PER_SECOND = 20;

	/**
	 * How long each drink's mana regeneration runs, and what the larger one restores the
	 * instant it is swallowed.
	 *
	 * <p>Both run for the same three seconds, and at
	 * {@code ManaRegenEffect.MANA_PER_TICK} that is <strong>30 mana each</strong> against
	 * a default pool of 100. So the two drinks differ in exactly one way — the potion
	 * pays 30 up front as well — which is the cleanest form the distinction can take:
	 * the vial <em>sustains</em> casting, the potion <em>restores</em> and then sustains.
	 *
	 * <p>Baked here rather than exposed in a config screen, which is the same call the
	 * weapons' damage and reach made: these describe two specific objects rather than a
	 * rule of the ruleset. Changing them costs a rebuild.
	 */
	private static final float VIAL_REGEN_SECONDS = 3.0F;
	private static final float POTION_REGEN_SECONDS = 3.0F;
	private static final float POTION_INSTANT_MANA = 30.0F;

	/**
	 * What the health drinks are worth, on the same three second window as the mana ones.
	 *
	 * <p>The vial returns ten health, and the potion thirty <em>in total</em> — twenty of
	 * it at once and the same ten over time the vial gives. So the potion is not a bigger
	 * vial; it is a vial plus an emergency, which is the distinction that decides which
	 * one you reach for.
	 *
	 * <p>Ten over three seconds is half a heart every six ticks, which is vanilla
	 * Regeneration IV exactly; {@code HealthPotionItem.REGEN_AMPLIFIER} explains why that
	 * level and no other lands on a whole number.
	 */
	private static final float HEALTH_VIAL_REGEN_SECONDS = 3.0F;
	private static final float HEALTH_POTION_REGEN_SECONDS = 3.0F;
	private static final float HEALTH_POTION_INSTANT = 20.0F;

	/**
	 * Vanilla's drinking interaction and nothing else: 1.6 seconds, the {@code DRINK}
	 * animation and the generic drink sound, none of it described here so none of it can
	 * drift from vanilla's.
	 *
	 * <p>Carries no {@code ApplyStatusEffectsConsumeEffect}, deliberately — the potion
	 * items apply their own effects so that drinks stack by adding their durations
	 * instead of vanilla's refresh-to-the-longest. See
	 * {@code com.hrtq.grandcraft.effect.StackingEffects}.
	 *
	 * <p>Shared by all four drinks precisely because it says nothing about what is in the
	 * bottle: what each one does is the item's business, and how it is drunk is identical.
	 *
	 * <p><strong>Declared before the items</strong>, like {@link #ALL}: this one is an
	 * object rather than a compile-time constant, so a field below the items would still
	 * be null when their initialisers run.
	 */
	private static final Consumable DRINK = Consumables.defaultDrink().build();

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
	 * A sip: mana over a short window, and nothing up front.
	 *
	 * <p>No {@code USE_REMAINDER}, so it leaves no empty bottle behind. That is the
	 * deliberate difference from a vanilla potion — these are consumed outright rather
	 * than decanted, so drinking one costs the container too.
	 */
	public static final Item MANA_VIAL = register("mana_vial",
			properties -> new ManaPotionItem(properties, 0.0F, regenTicks(VIAL_REGEN_SECONDS)),
			new Item.Properties()
					.stacksTo(16)
					.component(DataComponents.CONSUMABLE, DRINK));

	/**
	 * A draught: a chunk at once, then the same tail the vial has.
	 *
	 * <p>The instant chunk is what makes this the one to drink mid-fight — a tail alone
	 * cannot answer a spell you need to cast now, which is the whole distinction between
	 * this and the vial.
	 */
	public static final Item MANA_POTION = register("mana_potion",
			properties -> new ManaPotionItem(properties, POTION_INSTANT_MANA,
					regenTicks(POTION_REGEN_SECONDS)),
			new Item.Properties()
					.stacksTo(16)
					.component(DataComponents.CONSUMABLE, DRINK));

	/** A sip of health: ten over three seconds, and nothing up front. */
	public static final Item HEALTH_VIAL = register("health_vial",
			properties -> new HealthPotionItem(properties, 0.0F,
					regenTicks(HEALTH_VIAL_REGEN_SECONDS)),
			new Item.Properties()
					.stacksTo(16)
					.component(DataComponents.CONSUMABLE, DRINK));

	/**
	 * A draught of health: thirty in total, twenty of it immediately.
	 *
	 * <p>The instant twenty is what answers a hit you did not see coming; the tail is
	 * the vial's. A drink that healed thirty purely over time would be no use at the
	 * moment you most want one.
	 */
	public static final Item HEALTH_POTION = register("health_potion",
			properties -> new HealthPotionItem(properties, HEALTH_POTION_INSTANT,
					regenTicks(HEALTH_POTION_REGEN_SECONDS)),
			new Item.Properties()
					.stacksTo(16)
					.component(DataComponents.CONSUMABLE, DRINK));

	/**
	 * What a Mana Crystal breaks into: the mod's first crafting material.
	 *
	 * <p>A plain {@link Item} with no components at all, and that is the point — it has
	 * no behaviour to describe. How many one gets is not stated here either: the drop
	 * count and its Fortune scaling live in the block's loot table
	 * ({@code data/grandcraft/loot_table/blocks/mana_crystal.json}), because "what this
	 * block yields" is a property of the block, not of the thing it yields. That is the
	 * same item/config line the weapons draw, one level down.
	 *
	 * <p>Stacks to the default 64. The drinks stack to 16 because carrying them is meant
	 * to be a decision; a raw material is the opposite of that.
	 */
	public static final Item ARCANE_SHARD = register("arcane_shard", new Item.Properties());

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

	/** Drink durations are stated in seconds up here and spent in ticks everywhere else. */
	private static int regenTicks(float seconds) {
		return Math.round(seconds * TICKS_PER_SECOND);
	}

	/**
	 * Puts an item registered elsewhere into the mod's creative tab.
	 *
	 * <p>Exists for {@code GrandCraftBlocks}: a block's item is built by the block
	 * registration, not by {@link #register}, and there is no reason for a block to
	 * be missing from the tab because of where its item was made.
	 *
	 * <p>Safe to call after {@link #TAB} has been built — the tab's {@code
	 * displayItems} callback reads {@link #ALL} when the tab's contents are
	 * gathered, not when it is registered.
	 */
	public static void addToCreativeTab(Item item) {
		ALL.add(item);
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
