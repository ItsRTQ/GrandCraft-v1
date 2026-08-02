package com.hrtq.grandcraft.item;

import com.hrtq.grandcraft.magic.Spell;
import com.hrtq.grandcraft.magic.Spellcasting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The arcane implement: what a Sorcerer or Cleric casts with.
 *
 * <p>Its own class rather than a plain {@link Item} because right-click is going to
 * mean "cast" here and nowhere else. The staff is deliberately <strong>not</strong>
 * in {@code grandcraft:guard_implements}, which is what leaves the right button
 * free — a guard implement's hold is taken by the block verb.
 *
 * <p>Left-click stays an ordinary, weak melee swing. A caster who has run out of
 * mana should be embarrassing to be near, not helpless.
 *
 * <p>Deliberately thin. Everything about whether a cast is legal, what it costs and
 * what it produces lives in {@link Spellcasting}, so this file will not change when
 * real spells arrive — only the line that decides <em>which</em> spell will.
 */
public class StaffItem extends Item {
	public StaffItem(Properties properties) {
		super(properties);
	}

	/**
	 * Right-click casts.
	 *
	 * <p>Returns {@code FAIL} on the client so nothing is predicted locally: the
	 * server owns the mana and the legality, and a client that predicted a cast it
	 * could not afford would draw a cooldown for a gust that never left. This does
	 * not lose the input — vanilla builds and sends the use packet before it consults
	 * this result at all, so refusing here suppresses only the local prediction.
	 */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer caster)) {
			return InteractionResult.FAIL;
		}

		// The seam. When a caster can choose a spell this reads their choice instead of
		// naming one, and nothing else in this file changes.
		return Spellcasting.cast(serverLevel, caster, hand, Spell.GUST);
	}
}
