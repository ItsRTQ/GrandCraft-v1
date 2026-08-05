package com.hrtq.grandcraft.skill;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a character has equipped: three abilities on keys {@code 1}–{@code 3}, and one
 * ultimate on {@code 4}.
 *
 * <h2>The one part of the skill system that is a stored choice</h2>
 * Unlocking is derived and deliberately unstored — see {@link SkillUnlocks}. Equipping
 * is the opposite: nothing about the character implies it, so it has to be recorded,
 * and it is the first thing here that needs a packet to change.
 *
 * <p>That difference is the whole design. Unlocking says what you <em>could</em> use
 * and cannot be wrong, because it is recomputed; equipping says what you <em>chose</em>
 * and is remembered, because a choice nobody stored is not a choice.
 *
 * <h2>Slot four is not slot four</h2>
 * The ultimate is a named field rather than a fourth entry in a list, because it does
 * not obey the same rules: only ultimates go in it, only one is ever held, and an
 * ultimate cannot go anywhere else. A four-long list would need that asserted at every
 * use; a separate field states it once, in the type.
 *
 * <h2>Nodes are stored by path</h2>
 * {@link SkillNode#path()}, with the empty string meaning an empty slot. That keeps the
 * save file readable — a slot says {@code warrior.line_2.node_3} rather than {@code 7} —
 * and it fails safe: a path this character's tree does not contain resolves to nothing
 * and the slot reads empty, where a positional index would quietly resolve to a
 * <em>different</em> ability after any change to the tree's shape.
 */
public record SkillLoadout(String ability1, String ability2, String ability3, String ultimate) {

	/** Keys {@code 1}, {@code 2} and {@code 3}. */
	public static final int ABILITY_SLOTS = 3;

	/** The slot index of the ultimate — key {@code 4}, and one past the abilities. */
	public static final int ULTIMATE_SLOT = ABILITY_SLOTS;

	/** Every slot, ability and ultimate together. */
	public static final int SLOTS = ABILITY_SLOTS + 1;

	/** Nothing equipped. */
	public static final SkillLoadout EMPTY = new SkillLoadout("", "", "", "");

	public SkillLoadout {
		// A null read off disk or an old save would otherwise reach nodeByPath, which
		// tolerates it, and equals, which does not care — but every caller here treats
		// "empty" as the empty string, and two spellings of empty is how a slot ends up
		// looking full while holding nothing.
		ability1 = ability1 == null ? "" : ability1;
		ability2 = ability2 == null ? "" : ability2;
		ability3 = ability3 == null ? "" : ability3;
		ultimate = ultimate == null ? "" : ultimate;
	}

	/** The path in a slot, or the empty string. Slot 3 is the ultimate. */
	public String get(int slot) {
		return switch (slot) {
			case 0 -> this.ability1;
			case 1 -> this.ability2;
			case 2 -> this.ability3;
			default -> this.ultimate;
		};
	}

	/** A copy with one slot set, or cleared if given the empty string. */
	public SkillLoadout with(int slot, String path) {
		String value = path == null ? "" : path;

		return switch (slot) {
			case 0 -> new SkillLoadout(value, this.ability2, this.ability3, this.ultimate);
			case 1 -> new SkillLoadout(this.ability1, value, this.ability3, this.ultimate);
			case 2 -> new SkillLoadout(this.ability1, this.ability2, value, this.ultimate);
			default -> new SkillLoadout(this.ability1, this.ability2, this.ability3, value);
		};
	}

	/** Which slot holds this node, or -1. */
	public int slotOf(String path) {
		if (path == null || path.isEmpty()) {
			return -1;
		}

		for (int slot = 0; slot < SLOTS; slot++) {
			if (get(slot).equals(path)) {
				return slot;
			}
		}

		return -1;
	}

	public boolean holds(String path) {
		return slotOf(path) >= 0;
	}

	/**
	 * The lowest empty ability slot, or -1 when all three are taken.
	 *
	 * <p>Lowest rather than any, so equipping three abilities in order puts them on keys
	 * 1, 2 and 3 in that order — which is the only behaviour that is not a surprise.
	 */
	public int firstFreeAbilitySlot() {
		for (int slot = 0; slot < ABILITY_SLOTS; slot++) {
			if (get(slot).isEmpty()) {
				return slot;
			}
		}

		return -1;
	}

	public static final Codec<SkillLoadout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.optionalFieldOf("ability_1", EMPTY.ability1()).forGetter(SkillLoadout::ability1),
			Codec.STRING.optionalFieldOf("ability_2", EMPTY.ability2()).forGetter(SkillLoadout::ability2),
			Codec.STRING.optionalFieldOf("ability_3", EMPTY.ability3()).forGetter(SkillLoadout::ability3),
			Codec.STRING.optionalFieldOf("ultimate", EMPTY.ultimate()).forGetter(SkillLoadout::ultimate)
	).apply(instance, SkillLoadout::new));

	public static final StreamCodec<ByteBuf, SkillLoadout> STREAM_CODEC = StreamCodec.of(
			(buf, loadout) -> {
				ByteBufCodecs.STRING_UTF8.encode(buf, loadout.ability1());
				ByteBufCodecs.STRING_UTF8.encode(buf, loadout.ability2());
				ByteBufCodecs.STRING_UTF8.encode(buf, loadout.ability3());
				ByteBufCodecs.STRING_UTF8.encode(buf, loadout.ultimate());
			},
			// Java evaluates arguments left to right, so this matches the writes above.
			buf -> new SkillLoadout(
					ByteBufCodecs.STRING_UTF8.decode(buf),
					ByteBufCodecs.STRING_UTF8.decode(buf),
					ByteBufCodecs.STRING_UTF8.decode(buf),
					ByteBufCodecs.STRING_UTF8.decode(buf)));
}
