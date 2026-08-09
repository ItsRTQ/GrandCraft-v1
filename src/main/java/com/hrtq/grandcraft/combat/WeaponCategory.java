package com.hrtq.grandcraft.combat;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.stats.StatWeights;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What kind of weapon something is, and what that costs.
 *
 * <p>Adding a category is one entry here plus one tag file — the config file, the
 * packet and the screen tabs all derive from {@link #values()}, which is the same
 * bargain {@link CombatActor} strikes for mobs.
 *
 * <h2>Why a tag and not a data component</h2>
 * What counts as a heavy weapon is a judgement about the game, not a fact about the
 * item, and a datapack should be able to widen or narrow it without touching this
 * code. The obvious component, {@code DataComponents.WEAPON}, also does not mean
 * what it looks like: it means "loses durability when it attacks" and sits on every
 * pickaxe, shovel and hoe. Testing for it once let a player guard with a pickaxe.
 * See {@link GrandCraftTags}, which reaches the same conclusion for guarding.
 *
 * <h2>Two orders, deliberately different</h2>
 * <strong>Declaration order is display order</strong> — it is what the config screen
 * reads top to bottom, so it runs light to heavy and then the special cases. Match
 * precedence is a separate, explicit list in {@link #PRECEDENCE}, because the order
 * that reads well to a person and the order that resolves a tag conflict correctly
 * are not the same order, and leaving that implicit in the declaration made one of
 * them invisible.
 *
 * <h2>A category is a whole class of weapon, never one weapon</h2>
 * Everything here applies identically to the mod's own claymore, to a vanilla iron
 * sword, and to a sword from a mod this one has never heard of. An individual
 * weapon's damage, speed, reach and durability live on the item as vanilla data
 * components; what lives here is the ruleset's judgement about the class.
 *
 * <p>The intended next step is that an individual weapon may carry a
 * <em>multiplier</em> on top of its category's figures — a slightly heavier claymore
 * variant costing 1.2x the heavy stamina figure — rather than an absolute of its own.
 * That keeps one number to tune per category and makes every weapon a variation on
 * it, which is why these values are deliberately absolute and per-category today.
 */
public enum WeaponCategory implements StringRepresentable {
	/**
	 * Speed and cheapness. Twelve swings from full and almost immune to exhaustion,
	 * paid for with reach and per-hit damage.
	 */
	LIGHT("light", 0, 2, 0, 8, new StatWeights(20, 80, 0, 0), ArcaneSettings.NONE),

	/**
	 * Swords and axes: the middle of the road, and the baseline everything else is
	 * judged against.
	 *
	 * <p>Its values are exactly what the player had before weapons existed, so a
	 * vanilla iron sword must feel identical after this slice. If it does not, the
	 * change is confounded and every other category's numbers are being judged
	 * against a moving baseline.
	 */
	MEDIUM("medium", 0, 3, 0, 12, new StatWeights(60, 40, 0, 0), ArcaneSettings.NONE),

	/**
	 * Reach and commitment. Slow to recover from and expensive to swing, but it
	 * outranges what it is fighting.
	 *
	 * <p><strong>+5 on the wind-up</strong> (2026-08-07), which doubles the player's 5 into
	 * a 10 tick telegraph. It is the first modifier the seam was built for, and the larger
	 * of the two that exist ({@link #ARCANE} took +3 on the same grounds), and it was the
	 * animation that demanded it:
	 * the greatsword clip's wind-up is 18 ticks of authored motion, and played in 5 the
	 * held pose the whole telegraph consists of lasted under four. Ten puts it at the
	 * same 1.8x compression the confirmed sword plays at.
	 *
	 * <p>Committing twice as early is a real cost, and it is the one to watch:
	 * {@code tuning.md} lesson 7 is a mob that could never land a telegraphed blow on
	 * anyone walking backwards. The claymore's answer is reach — 4.0 against a sword's
	 * 3.0 — and the hit window of 5 here, the widest of any category, is the other half
	 * of it. If it still whiffs on a retreating target, that window is the number.
	 *
	 * <p>Endlag takes no modifier, so a claymore leaves as fast as a sword does. That is
	 * a decision waiting to be made rather than one made here: the pre-global config
	 * carried 40 ticks of claymore recovery, which is not a number anyone judged by eye.
	 *
	 * <p>Cost 20 gives five swings from a full pool, and sustained swinging never
	 * lets the ten tick regen delay expire — so a claymore user cannot both swing and
	 * turtle, which is the decision the weapon exists to create.
	 */
	HEAVY("heavy", 5, 5, 0, 20, new StatWeights(100, 0, 0, 0), ArcaneSettings.NONE),

	/**
	 * The caster's implement. Poor in melee, and not a guard implement — which is
	 * what leaves the right button free to mean "cast".
	 *
	 * <p><strong>+3 on the wind-up</strong> (2026-08-09), for {@link #HEAVY}'s reason
	 * exactly and no other: the staff clip delivered that day raises over 15 ticks, and
	 * played in the global 5 the held pose the whole telegraph consists of lasts two.
	 * Eight ticks is ~1.9x compression, the band both confirmed clips play in. It is a
	 * smaller number than HEAVY's because the staff's raise is shorter than the
	 * greatsword's, not because a caster is meant to commit less — this is an animation
	 * figure, and the melee weight of a staff was never the thing being tuned.
	 *
	 * <p>Unlike HEAVY's, this modifier has <em>not</em> been judged by eye yet. The
	 * arithmetic that produced it is the same arithmetic that produced HEAVY's, which
	 * was confirmed on sight; that is the whole of the evidence for it.
	 */
	ARCANE("arcane", 3, 3, 0, 10, new StatWeights(0, 20, 0, 80),
			// Knockback is deliberately zero: a gust that shoved was too good at
			// creating distance for free, on an attack that costs nothing to hold.
			// The mechanism is kept rather than deleted so a heavier spell can use it.
			//
			// A 16 tick base is 0.8 seconds before Arcane, which a high-Arcane caster
			// shortens to about 13 and a low one lengthens to about 18.
			new ArcaneSettings(8, 200, 150, 0, 16, 20)),

	/**
	 * Bows and crossbows.
	 *
	 * <p>Exists mainly so a bow does not fall through to {@link #UNARMED} and so the
	 * ranged slice has somewhere to land. Today it only sets the hit window and cost of
	 * bashing something with a bow, which is not a thing anyone does on purpose — do not
	 * expect to feel this one yet, and do not tune it by feel.
	 */
	RANGED("ranged", 0, 2, 0, 6, new StatWeights(20, 80, 0, 0), ArcaneSettings.NONE),

	/**
	 * Bare hands, and anything the tags do not claim — a pickaxe, a fishing rod, a
	 * stack of dirt.
	 *
	 * <p>A real category rather than a null fallback, which is what lets {@link #of}
	 * always return an answer and removes a null branch from every caller. It also
	 * makes punching tunable, and turns swinging the wrong tool at a mob from an
	 * unhandled case into a deliberate penalty: no reach, no damage bonus, and
	 * unarmed endlag.
	 *
	 * <p>Its <em>timings and cost</em> must stay identical to {@link #MEDIUM}'s for the
	 * same reason MEDIUM's must — punching silently changing is a report nobody would
	 * connect to a weapon slice.
	 *
	 * <p>Its scaling weights are the one thing that deliberately differs, and they can,
	 * because there was no pre-weapon punching baseline for them to preserve. A fist is
	 * more purely Strength than a sword is (70/30 against 60/40), which is also what
	 * keeps the useful accident that punching beats swinging a claymore you cannot lift.
	 */
	UNARMED("unarmed", 0, 2, 0, 12, new StatWeights(70, 30, 0, 0), ArcaneSettings.NONE, null);

	private final String id;
	private final TagKey<Item> tag;
	private final CategorySettings defaults;
	private final Codec<CategorySettings> settingsCodec;

	/**
	 * @param startupModifier signed ticks added to the actor's global wind-up, and
	 *                        likewise {@code recoveryModifier} for its endlag — see
	 *                        {@link CategorySettings}. Zero is a category that does not
	 *                        argue with the rhythm, which is most of them.
	 * @param active the hit window, which is a length rather than a modifier: it is the
	 *               one phase still owned by the weapon, because whether a telegraphed
	 *               swing can connect only means anything against that weapon's reach.
	 */
	WeaponCategory(String id, int startupModifier, int active, int recoveryModifier, int cost,
			StatWeights weights, ArcaneSettings arcane) {
		this(id, startupModifier, active, recoveryModifier, cost, weights, arcane,
				TagKey.create(Registries.ITEM, GrandCraft.id(id + "_weapons")));
	}

	WeaponCategory(String id, int startupModifier, int active, int recoveryModifier, int cost,
			StatWeights weights, ArcaneSettings arcane, TagKey<Item> tag) {
		this.id = id;
		this.tag = tag;
		this.defaults =
				new CategorySettings(startupModifier, active, recoveryModifier, cost, weights, arcane);
		// Its own codec, built from its own defaults, so a category missing from the
		// config file falls back to its own values rather than to some shared blank.
		this.settingsCodec = CategorySettings.codec(this.defaults);
	}

	/**
	 * The order {@link #of} tests tags in, most specific claim first.
	 *
	 * <p>{@link #MEDIUM} is last because its tag holds {@code #minecraft:swords} and
	 * {@code #minecraft:axes} — the widest claims, and the ones a datapack is most
	 * likely to double-list. Every other tag is a narrower claim, and the narrower
	 * claim should win: someone who adds a katana to {@code light_weapons} is asking
	 * for it to be light, not telling you it is also a sword.
	 *
	 * <p>{@link #UNARMED} is absent because it has no tag; it is what {@code of}
	 * falls through to.
	 */
	private static final WeaponCategory[] PRECEDENCE = {HEAVY, LIGHT, ARCANE, RANGED, MEDIUM};

	/**
	 * The category a held stack belongs to. Never null — an unclaimed item is
	 * {@link #UNARMED}.
	 *
	 * <p>Not cached, deliberately. This is at most five tag-membership tests and it
	 * runs on the attack event, not on a tick path. The right scope for "which weapon
	 * is this swing" is the swing itself, and {@code CombatController} already latches
	 * that once per attack — a stack-keyed cache would be a second, staler answer to a
	 * question that is already answered correctly.
	 */
	public static WeaponCategory of(ItemStack stack) {
		if (stack != null && !stack.isEmpty()) {
			for (WeaponCategory category : PRECEDENCE) {
				if (stack.is(category.tag)) {
					return category;
				}
			}
		}

		return UNARMED;
	}

	public String id() {
		return this.id;
	}

	/** The tag that claims items for this category, or null for {@link #UNARMED}. */
	public TagKey<Item> tag() {
		return this.tag;
	}

	public CategorySettings defaults() {
		return this.defaults;
	}

	public Codec<CategorySettings> settingsCodec() {
		return this.settingsCodec;
	}

	/**
	 * Whether this category casts, and therefore has cast values worth showing and
	 * reading. Everything else carries a zeroed {@link ArcaneSettings} so the config
	 * tells the truth about it rather than hiding a field that exists.
	 */
	public boolean casts() {
		return this == ARCANE;
	}

	@Override
	public String getSerializedName() {
		return this.id;
	}

	public Component displayName() {
		return Component.translatable("weapon.grandcraft.category." + this.id);
	}

	/**
	 * One line saying what this category covers, shown under the config screen's tab
	 * row.
	 *
	 * <p>Exists because the screen was reported as reading like a per-weapon editor
	 * when it has always been a per-category one. Naming the members is the shortest
	 * possible answer to "does this affect one weapon or all of them".
	 *
	 * <p>Returns {@link MutableComponent}, not {@code Component}: the caller styles it
	 * grey, and {@code withStyle} exists only on the mutable form.
	 */
	public MutableComponent description() {
		return Component.translatable("weapon.grandcraft.category." + this.id + ".description");
	}
}
