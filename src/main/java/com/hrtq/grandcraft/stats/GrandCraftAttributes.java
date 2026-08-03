package com.hrtq.grandcraft.stats;

import com.hrtq.grandcraft.GrandCraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * The four character stats, registered as real Minecraft attributes.
 *
 * <h2>Why attributes rather than a data attachment</h2>
 * A stat has to be persisted, synced to the owning client, and modifiable by things
 * that do not exist yet. Vanilla's attribute system already does all three:
 * base values are saved with the player and copied across a respawn, a
 * {@code syncable} attribute reaches its owner's client with no packet of ours, and
 * gear will be able to grant {@code +2 Strength} through the item's own attribute
 * modifiers with no code here at all. An attachment would mean reimplementing each
 * of those, and the last one twice.
 *
 * <h2>Player only</h2>
 * These are added by {@code PlayerAttributesMixin} to {@code Player.createAttributes}
 * alone. Stats come from the class table, which mobs have no part in, and mobs
 * already express their power through {@code RolledStats} — a second system doing
 * the same job is exactly the duplication {@code CombatActor} exists to prevent.
 *
 * <p>The consequence is that <strong>every read must tolerate a missing
 * instance</strong>. {@code StatEffects.statOf} is the one place that does, and
 * nothing should read a stat any other way.
 *
 * <h2>Registration timing</h2>
 * The registration happens in these static field initialisers, not in
 * {@link #register()}, which is the project's usual convention and here is
 * load-bearing: {@code Player.createAttributes} is called from
 * {@code DefaultAttributes}' static initialiser, whose ordering against
 * {@code onInitialize} is not guaranteed. The mixin's reference to a field of this
 * class is what forces the registration to have happened by the time it is needed.
 */
public final class GrandCraftAttributes {
	/**
	 * Melee damage, through whatever is being swung — see {@code MeleeDamage}. Heavy weapons read
	 * it almost exclusively, swords partly, and it gates most of them outright.
	 */
	public static final Holder<Attribute> STRENGTH = register("strength");

	/** Lowers every stamina cost. Later: light weapons, medium armour, archery. */
	public static final Holder<Attribute> AGILITY = register("agility");

	/** Armour, a little maximum health, and how fast stamina comes back. */
	public static final Holder<Attribute> CONSTITUTION = register("constitution");

	/**
	 * Spell damage, through {@link ArcaneScaling}.
	 *
	 * <p>Read at the point of a cast rather than as an attribute modifier, for the
	 * same reason Agility's stamina effect is: the configured rate is a whole number,
	 * and a slight per-point effect folded back into one would round away entirely.
	 *
	 * <p>The mana pool and its recovery are still flat config values that this does
	 * not touch — worth revisiting once there is more than one thing to spend on.
	 */
	public static final Holder<Attribute> ARCANE = register("arcane");

	private GrandCraftAttributes() {
	}

	private static Holder<Attribute> register(String path) {
		// setSyncable is not optional: AttributeMap only queues an instance for the
		// client when the attribute reports itself syncable, so without it the
		// character sheet would read client-side defaults and never say why.
		Attribute attribute = new RangedAttribute(
				"attribute.name.grandcraft." + path,
				StatConstants.NEUTRAL, StatConstants.MIN, StatConstants.MAX)
				.setSyncable(true)
				.setSentiment(Attribute.Sentiment.POSITIVE);

		return Registry.registerForHolder(BuiltInRegistries.ATTRIBUTE, GrandCraft.id(path), attribute);
	}

	public static void register() {
		// Attribute types are registered by the static initializers above.
	}
}
