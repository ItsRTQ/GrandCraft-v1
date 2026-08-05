package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.skill.SkillLoadout;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: I pressed one of the four ability keys.
 *
 * <p>Carries the <em>slot</em>, not the ability. What is in that slot is the server's
 * own record, so a client cannot ask to use something it has not equipped — the worst
 * a forged packet can do is fire whatever the sender already had on that key.
 *
 * <p>No prediction of any kind, like every other verb in this mod. The client sends and
 * says nothing; everything the player sees comes back as ordinary server-side effect.
 */
public record UseSkillPayload(int slot) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<UseSkillPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("use_skill"));

	public static final StreamCodec<ByteBuf, UseSkillPayload> STREAM_CODEC =
			ByteBufCodecs.VAR_INT.map(UseSkillPayload::new, UseSkillPayload::slot);

	/** Whether the slot on the wire is one that exists. Checked by the receiver. */
	public boolean isValidSlot() {
		return this.slot >= 0 && this.slot < SkillLoadout.SLOTS;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
