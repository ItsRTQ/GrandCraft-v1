package com.hrtq.grandcraft.skill;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * One character's progress towards their skill-line milestones: how much of each
 * {@link SkillObjective} they have done.
 *
 * <p>Shaped like {@code StatBlock} and {@code PoolBlock} and for the same reasons —
 * one field per constant, immutable, with the two codecs needed to persist and sync
 * it. A map would serialise to something less readable in the save file and buy
 * nothing at this size.
 *
 * <h2>What is not here: which nodes are unlocked</h2>
 * Nothing. Unlocking is <strong>derived</strong> from these counters and the
 * character's level every time it is asked — see {@link SkillUnlocks}. There is
 * deliberately no stored "unlocked" set, because a stored one can disagree with the
 * reasons it was written, and that disagreement is unfixable after the fact.
 *
 * <p>The visible consequence: raising a level gate re-locks nodes that were open.
 * That is correct rather than unfortunate — the gate is what unlocking <em>means</em>,
 * so an admin who moves it has moved what is unlocked.
 */
public record SkillProgress(int slain, int struck, int evaded, int slainWithSkill) {

	/** A character who has not yet done anything. */
	public static final SkillProgress NONE = new SkillProgress(0, 0, 0, 0);

	public SkillProgress {
		// Never meaningfully negative, and a negative read off disk would make a
		// milestone unreachable rather than fail loudly. Clamping in the canonical
		// constructor covers codec, packet and increment in one guard — the same rule
		// EssenceProgress follows.
		slain = Math.max(slain, 0);
		struck = Math.max(struck, 0);
		evaded = Math.max(evaded, 0);
		slainWithSkill = Math.max(slainWithSkill, 0);
	}

	/** How much of one objective has been done. */
	public int get(SkillObjective objective) {
		return switch (objective) {
			case SLAY -> this.slain;
			case STRIKE -> this.struck;
			case EVADE -> this.evaded;
			case SLAY_WITH_SKILL -> this.slainWithSkill;
		};
	}

	/** A copy with {@code amount} more of one objective. */
	public SkillProgress plus(SkillObjective objective, int amount) {
		return switch (objective) {
			case SLAY -> new SkillProgress(
					this.slain + amount, this.struck, this.evaded, this.slainWithSkill);
			case STRIKE -> new SkillProgress(
					this.slain, this.struck + amount, this.evaded, this.slainWithSkill);
			case EVADE -> new SkillProgress(
					this.slain, this.struck, this.evaded + amount, this.slainWithSkill);
			case SLAY_WITH_SKILL -> new SkillProgress(
					this.slain, this.struck, this.evaded, this.slainWithSkill + amount);
		};
	}

	public static final Codec<SkillProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			// Optional like every other record here, so a character saved before an
			// objective existed loads with none of it rather than failing to load.
			Codec.INT.optionalFieldOf("slain", NONE.slain()).forGetter(SkillProgress::slain),
			Codec.INT.optionalFieldOf("struck", NONE.struck()).forGetter(SkillProgress::struck),
			Codec.INT.optionalFieldOf("evaded", NONE.evaded()).forGetter(SkillProgress::evaded),
			Codec.INT.optionalFieldOf("slain_with_skill", NONE.slainWithSkill())
					.forGetter(SkillProgress::slainWithSkill)
	).apply(instance, SkillProgress::new));

	public static final StreamCodec<ByteBuf, SkillProgress> STREAM_CODEC = StreamCodec.of(
			(buf, progress) -> {
				buf.writeInt(progress.slain());
				buf.writeInt(progress.struck());
				buf.writeInt(progress.evaded());
				buf.writeInt(progress.slainWithSkill());
			},
			// Java evaluates arguments left to right, so this matches the writes above.
			buf -> new SkillProgress(
					buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));
}
