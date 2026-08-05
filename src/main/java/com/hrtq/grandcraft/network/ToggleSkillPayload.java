package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: I clicked this skill node — equip it, or take it off if it is
 * already on.
 *
 * <p>Carries the node and <strong>not the slot</strong>. Which slot an ability lands
 * in, whether it is an ultimate and therefore belongs in the fourth, and whether it is
 * unlocked at all are the server's to decide — see {@code SkillLoadouts.toggle}. A
 * packet that named a slot could express an illegal request; this one cannot.
 *
 * <p>The path is untrusted text. It is looked up in the sending player's <em>own</em>
 * tree, so a path naming another class's node, a node that does not exist, or the root
 * resolves to nothing and the request is refused.
 */
public record ToggleSkillPayload(String path) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ToggleSkillPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("toggle_skill"));

	/**
	 * Length-capped, because this is a string from a client and the receiver looks it
	 * up rather than parsing it. A real path is well under this; anything longer is not
	 * a path and is refused before it reaches the lookup.
	 */
	private static final int MAX_PATH = 128;

	public static final StreamCodec<ByteBuf, ToggleSkillPayload> STREAM_CODEC =
			ByteBufCodecs.stringUtf8(MAX_PATH)
					.map(ToggleSkillPayload::new, ToggleSkillPayload::path);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
