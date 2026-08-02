package com.hrtq.grandcraft.magic;

import net.minecraft.util.StringRepresentable;

/**
 * A thing an arcane implement can cast.
 *
 * <p>One constant today, and that is the point: the staff needs something to do
 * before there is a spell system, and giving that thing a name now means the spell
 * system arrives as new constants and new arms of one switch rather than as a rewrite
 * of the item.
 *
 * <p>An enum rather than a registry deliberately, for as long as the list is short:
 * it is wire-safe, switchable and iterable, which is the same bargain
 * {@code CharacterStat} and {@code CharacterPool} strike. A registry becomes the right
 * answer when spells are data rather than code, and that is a decision to make with a
 * real spell list in hand rather than with one.
 */
public enum Spell implements StringRepresentable {
	/**
	 * The staff's default attack: what a caster with no spell selected throws.
	 *
	 * <p>Deliberately modest. This is the attack you have when you have nothing
	 * better, and it being unremarkable is exactly why learning a real spell will be
	 * worth doing.
	 */
	GUST("gust");

	private final String id;

	Spell(String id) {
		this.id = id;
	}

	@Override
	public String getSerializedName() {
		return this.id;
	}
}
